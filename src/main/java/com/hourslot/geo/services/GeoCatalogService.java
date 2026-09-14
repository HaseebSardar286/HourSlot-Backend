package com.hourslot.geo.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hourslot.geo.dto.CountryView;
import com.hourslot.geo.dto.CurrencyView;
import com.hourslot.geo.dto.RegionView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live country / region / city / currency catalog from REST Countries and CountriesNow,
 * with an offline fallback so dropdowns still work if the public APIs are unreachable.
 */
@Service
public class GeoCatalogService {

    private static final Logger log = LogManager.getLogger(GeoCatalogService.class);
    private static final long COUNTRY_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final long CITY_TTL_MS = 12 * 60 * 60 * 1000L;
    private static final Pattern UTC_OFFSET = Pattern.compile("UTC([+-])(\\d{2}):?(\\d{2})?");

    private static final String REST_COUNTRIES =
            "https://restcountries.com/v3.1/all?fields=name,cca2,cca3,currencies,timezones,flag,region,subregion,capital";
    private static final String COUNTRIES_NOW_STATES = "https://countriesnow.space/api/v0.1/countries/states";
    private static final String COUNTRIES_NOW_CITIES = "https://countriesnow.space/api/v0.1/countries/state/cities";

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private volatile List<CountryView> countries = List.of();
    private volatile long countriesCachedAt;
    private volatile Map<String, List<RegionView>> statesByKey = Map.of();
    private volatile long statesCachedAt;

    private final ConcurrentHashMap<String, CachedCities> citiesCache = new ConcurrentHashMap<>();

    public GeoCatalogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.countries = fallbackCountries();
        this.statesByKey = fallbackStates();
    }

    @PostConstruct
    public void warmup() {
        Thread warmer = new Thread(() -> {
            try {
                listCountries();
                listStates("PK");
            } catch (Exception e) {
                log.warn("Geo catalog warmup failed: {}", e.getMessage());
            }
        }, "geo-catalog-warmup");
        warmer.setDaemon(true);
        warmer.start();
    }

    public List<CountryView> listCountries() {
        refreshCountriesIfNeeded();
        return countries;
    }

    public List<CurrencyView> listCurrencies() {
        Map<String, CurrencyView> unique = new LinkedHashMap<>();
        for (CountryView country : listCountries()) {
            if (country.getCurrencies() == null) {
                continue;
            }
            for (CurrencyView currency : country.getCurrencies()) {
                if (currency.getCode() != null && !unique.containsKey(currency.getCode())) {
                    unique.put(currency.getCode(), currency);
                }
            }
        }
        List<CurrencyView> list = new ArrayList<>(unique.values());
        list.sort(Comparator.comparing(CurrencyView::getCode));
        return list;
    }

    public List<RegionView> listStates(String countryCodeOrName) {
        refreshStatesIfNeeded();
        CountryView country = resolveCountry(countryCodeOrName);
        if (country == null) {
            return List.of();
        }
        List<RegionView> byCode = statesByKey.get(key(country.getCode()));
        if (byCode != null && !byCode.isEmpty()) {
            return byCode;
        }
        List<RegionView> byName = statesByKey.get(key(country.getName()));
        return byName == null ? List.of() : byName;
    }

    public List<String> listCities(String countryCodeOrName, String stateName) {
        CountryView country = resolveCountry(countryCodeOrName);
        if (country == null || stateName == null || stateName.isBlank()) {
            return List.of();
        }
        String cacheKey = key(country.getCode()) + "|" + key(stateName);
        CachedCities cached = citiesCache.get(cacheKey);
        if (cached != null && !cached.expired()) {
            return cached.cities;
        }

        List<String> fetched = fetchCities(country.getName(), stateName.trim());
        if (fetched.isEmpty()) {
            fetched = fallbackCities(stateName.trim());
        }
        citiesCache.put(cacheKey, new CachedCities(fetched, System.currentTimeMillis()));
        return fetched;
    }

    public List<String> listTimezones(String countryCodeOrName) {
        CountryView country = resolveCountry(countryCodeOrName);
        List<String> zones = new ArrayList<>();
        if (country != null && country.getTimezones() != null) {
            zones.addAll(country.getTimezones());
        }
        ZoneOffset target = firstOffset(country);
        if (target != null) {
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            for (String id : ZoneId.getAvailableZoneIds()) {
                if (id.startsWith("Etc/") || id.startsWith("SystemV/") || id.contains("GMT")) {
                    continue;
                }
                ZoneId zoneId = ZoneId.of(id);
                if (now.withZoneSameInstant(zoneId).getOffset().equals(target) && !zones.contains(id)) {
                    zones.add(id);
                }
            }
        }
        zones.sort(String::compareTo);
        return zones;
    }

    public CountryView resolveCountry(String countryCodeOrName) {
        if (countryCodeOrName == null || countryCodeOrName.isBlank()) {
            return null;
        }
        String needle = countryCodeOrName.trim();
        for (CountryView country : listCountries()) {
            if (needle.equalsIgnoreCase(country.getCode())
                    || needle.equalsIgnoreCase(country.getIso3())
                    || needle.equalsIgnoreCase(country.getName())) {
                return country;
            }
        }
        return null;
    }

    private void refreshCountriesIfNeeded() {
        if (countriesCachedAt > 0 && System.currentTimeMillis() - countriesCachedAt < COUNTRY_TTL_MS
                && countries.size() > 20) {
            return;
        }
        synchronized (this) {
            if (countriesCachedAt > 0 && System.currentTimeMillis() - countriesCachedAt < COUNTRY_TTL_MS
                    && countries.size() > 20) {
                return;
            }
            try {
                JsonNode root = getJson(REST_COUNTRIES);
                if (root != null && root.isArray() && root.size() > 0) {
                    List<CountryView> parsed = new ArrayList<>();
                    for (JsonNode node : root) {
                        CountryView view = parseCountry(node);
                        if (view != null && view.getCode() != null) {
                            parsed.add(view);
                        }
                    }
                    parsed.sort(Comparator.comparing(CountryView::getName, String.CASE_INSENSITIVE_ORDER));
                    if (!parsed.isEmpty()) {
                        countries = List.copyOf(parsed);
                        countriesCachedAt = System.currentTimeMillis();
                        log.info("Loaded {} countries from REST Countries", parsed.size());
                    }
                }
            } catch (Exception e) {
                log.warn("REST Countries lookup failed, using fallback catalog: {}", e.getMessage());
                if (countries.isEmpty()) {
                    countries = fallbackCountries();
                }
            }
        }
    }

    private void refreshStatesIfNeeded() {
        if (statesCachedAt > 0 && System.currentTimeMillis() - statesCachedAt < COUNTRY_TTL_MS
                && statesByKey.size() > 10) {
            return;
        }
        synchronized (this) {
            if (statesCachedAt > 0 && System.currentTimeMillis() - statesCachedAt < COUNTRY_TTL_MS
                    && statesByKey.size() > 10) {
                return;
            }
            try {
                JsonNode root = getJson(COUNTRIES_NOW_STATES);
                JsonNode data = root == null ? null : root.get("data");
                if (data != null && data.isArray()) {
                    Map<String, List<RegionView>> map = new LinkedHashMap<>();
                    for (JsonNode countryNode : data) {
                        String name = text(countryNode, "name");
                        String iso2 = text(countryNode, "iso2");
                        JsonNode states = countryNode.get("states");
                        List<RegionView> regions = new ArrayList<>();
                        if (states != null && states.isArray()) {
                            for (JsonNode state : states) {
                                String stateName = text(state, "name");
                                if (stateName == null || stateName.isBlank()) {
                                    continue;
                                }
                                regions.add(new RegionView(stateName, text(state, "state_code")));
                            }
                        }
                        regions.sort(Comparator.comparing(RegionView::getName, String.CASE_INSENSITIVE_ORDER));
                        List<RegionView> frozen = List.copyOf(regions);
                        if (name != null) {
                            map.put(key(name), frozen);
                        }
                        if (iso2 != null) {
                            map.put(key(iso2), frozen);
                        }
                    }
                    if (!map.isEmpty()) {
                        statesByKey = Map.copyOf(map);
                        statesCachedAt = System.currentTimeMillis();
                        log.info("Loaded states for {} country keys from CountriesNow", map.size());
                    }
                }
            } catch (Exception e) {
                log.warn("CountriesNow states lookup failed, using fallback: {}", e.getMessage());
                if (statesByKey.isEmpty()) {
                    statesByKey = fallbackStates();
                }
            }
        }
    }

    private List<String> fetchCities(String countryName, String stateName) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "country", countryName,
                    "state", stateName));
            JsonNode root = postJson(COUNTRIES_NOW_CITIES, body);
            if (root != null && !root.path("error").asBoolean(false) && root.path("data").isArray()) {
                List<String> cities = new ArrayList<>();
                for (JsonNode node : root.get("data")) {
                    String city = node.isTextual() ? node.asText() : text(node, "name");
                    if (city != null && !city.isBlank()) {
                        cities.add(city);
                    }
                }
                cities.sort(String.CASE_INSENSITIVE_ORDER);
                return List.copyOf(cities);
            }
        } catch (Exception e) {
            log.warn("CountriesNow cities lookup failed for {} / {}: {}", countryName, stateName, e.getMessage());
        }
        return List.of();
    }

    private CountryView parseCountry(JsonNode node) {
        JsonNode nameNode = node.path("name");
        String common = text(nameNode, "common");
        if (common == null) {
            return null;
        }
        List<CurrencyView> currencies = new ArrayList<>();
        JsonNode currencyNode = node.get("currencies");
        if (currencyNode != null && currencyNode.isObject()) {
            currencyNode.fields().forEachRemaining(entry -> {
                JsonNode details = entry.getValue();
                currencies.add(new CurrencyView(
                        entry.getKey(),
                        text(details, "name"),
                        text(details, "symbol")));
            });
        }
        List<String> timezones = new ArrayList<>();
        JsonNode tzNode = node.get("timezones");
        if (tzNode != null && tzNode.isArray()) {
            tzNode.forEach(tz -> timezones.add(tz.asText()));
        }
        String capital = null;
        JsonNode capitalNode = node.get("capital");
        if (capitalNode != null && capitalNode.isArray() && capitalNode.size() > 0) {
            capital = capitalNode.get(0).asText();
        }
        return CountryView.builder()
                .code(text(node, "cca2"))
                .iso3(text(node, "cca3"))
                .name(common)
                .officialName(text(nameNode, "official"))
                .flag(text(node, "flag"))
                .region(text(node, "region"))
                .subregion(text(node, "subregion"))
                .capital(capital)
                .timezones(timezones)
                .currencies(currencies)
                .build();
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("User-Agent", "HourSlot/1.0 (geo catalog)")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url);
        }
        return objectMapper.readTree(response.body());
    }

    private JsonNode postJson(String url, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "HourSlot/1.0 (geo catalog)")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url);
        }
        return objectMapper.readTree(response.body());
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static ZoneOffset firstOffset(CountryView country) {
        if (country == null || country.getTimezones() == null) {
            return null;
        }
        for (String tz : country.getTimezones()) {
            Matcher matcher = UTC_OFFSET.matcher(tz == null ? "" : tz);
            if (matcher.find()) {
                int hours = Integer.parseInt(matcher.group(2));
                int minutes = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
                int total = hours * 3600 + minutes * 60;
                if ("-".equals(matcher.group(1))) {
                    total = -total;
                }
                return ZoneOffset.ofTotalSeconds(total);
            }
        }
        return null;
    }

    private static List<CountryView> fallbackCountries() {
        return List.of(
                country("PK", "PAK", "Pakistan", "🇵🇰", "Asia", "Southern Asia", "Islamabad",
                        List.of("UTC+05:00"), List.of(currency("PKR", "Pakistani Rupee", "₨"))),
                country("AE", "ARE", "United Arab Emirates", "🇦🇪", "Asia", "Western Asia", "Abu Dhabi",
                        List.of("UTC+04:00"), List.of(currency("AED", "UAE Dirham", "د.إ"))),
                country("SA", "SAU", "Saudi Arabia", "🇸🇦", "Asia", "Western Asia", "Riyadh",
                        List.of("UTC+03:00"), List.of(currency("SAR", "Saudi Riyal", "ر.س"))),
                country("US", "USA", "United States", "🇺🇸", "Americas", "North America", "Washington, D.C.",
                        List.of("UTC-05:00", "UTC-08:00"), List.of(currency("USD", "United States dollar", "$"))),
                country("GB", "GBR", "United Kingdom", "🇬🇧", "Europe", "Northern Europe", "London",
                        List.of("UTC+00:00"), List.of(currency("GBP", "British pound", "£"))),
                country("IN", "IND", "India", "🇮🇳", "Asia", "Southern Asia", "New Delhi",
                        List.of("UTC+05:30"), List.of(currency("INR", "Indian rupee", "₹"))),
                country("CA", "CAN", "Canada", "🇨🇦", "Americas", "North America", "Ottawa",
                        List.of("UTC-05:00"), List.of(currency("CAD", "Canadian dollar", "$"))),
                country("AU", "AUS", "Australia", "🇦🇺", "Oceania", "Australia and New Zealand", "Canberra",
                        List.of("UTC+10:00"), List.of(currency("AUD", "Australian dollar", "$"))),
                country("DE", "DEU", "Germany", "🇩🇪", "Europe", "Western Europe", "Berlin",
                        List.of("UTC+01:00"), List.of(currency("EUR", "Euro", "€"))),
                country("FR", "FRA", "France", "🇫🇷", "Europe", "Western Europe", "Paris",
                        List.of("UTC+01:00"), List.of(currency("EUR", "Euro", "€"))),
                country("TR", "TUR", "Turkey", "🇹🇷", "Asia", "Western Asia", "Ankara",
                        List.of("UTC+03:00"), List.of(currency("TRY", "Turkish lira", "₺"))),
                country("EG", "EGY", "Egypt", "🇪🇬", "Africa", "Northern Africa", "Cairo",
                        List.of("UTC+02:00"), List.of(currency("EGP", "Egyptian pound", "£"))),
                country("QA", "QAT", "Qatar", "🇶🇦", "Asia", "Western Asia", "Doha",
                        List.of("UTC+03:00"), List.of(currency("QAR", "Qatari riyal", "ر.ق"))),
                country("KW", "KWT", "Kuwait", "🇰🇼", "Asia", "Western Asia", "Kuwait City",
                        List.of("UTC+03:00"), List.of(currency("KWD", "Kuwaiti dinar", "د.ك"))),
                country("BD", "BGD", "Bangladesh", "🇧🇩", "Asia", "Southern Asia", "Dhaka",
                        List.of("UTC+06:00"), List.of(currency("BDT", "Bangladeshi taka", "৳")))
        );
    }

    private static CountryView country(
            String code, String iso3, String name, String flag, String region, String subregion,
            String capital, List<String> timezones, List<CurrencyView> currencies) {
        return CountryView.builder()
                .code(code)
                .iso3(iso3)
                .name(name)
                .officialName(name)
                .flag(flag)
                .region(region)
                .subregion(subregion)
                .capital(capital)
                .timezones(timezones)
                .currencies(currencies)
                .build();
    }

    private static CurrencyView currency(String code, String name, String symbol) {
        return new CurrencyView(code, name, symbol);
    }

    private static Map<String, List<RegionView>> fallbackStates() {
        Map<String, List<RegionView>> map = new LinkedHashMap<>();
        putStates(map, "PK", "Pakistan", List.of(
                "Punjab", "Sindh", "Khyber Pakhtunkhwa", "Balochistan",
                "Islamabad Capital Territory", "Gilgit-Baltistan", "Azad Kashmir"));
        putStates(map, "AE", "United Arab Emirates", List.of("Dubai", "Abu Dhabi", "Sharjah", "Ajman", "Ras Al Khaimah", "Fujairah", "Umm Al Quwain"));
        putStates(map, "SA", "Saudi Arabia", List.of("Riyadh", "Makkah", "Madinah", "Eastern Province", "Asir", "Tabuk"));
        putStates(map, "US", "United States", List.of("California", "New York", "Texas", "Florida", "Illinois", "Washington"));
        putStates(map, "GB", "United Kingdom", List.of("England", "Scotland", "Wales", "Northern Ireland"));
        putStates(map, "IN", "India", List.of("Maharashtra", "Delhi", "Karnataka", "Tamil Nadu", "Uttar Pradesh", "Gujarat"));
        putStates(map, "CA", "Canada", List.of("Ontario", "Quebec", "British Columbia", "Alberta"));
        putStates(map, "AU", "Australia", List.of("New South Wales", "Victoria", "Queensland", "Western Australia"));
        putStates(map, "DE", "Germany", List.of("Bavaria", "Berlin", "Hamburg", "North Rhine-Westphalia"));
        putStates(map, "FR", "France", List.of("Île-de-France", "Provence-Alpes-Côte d'Azur", "Auvergne-Rhône-Alpes"));
        return Map.copyOf(map);
    }

    private static void putStates(Map<String, List<RegionView>> map, String code, String name, List<String> states) {
        List<RegionView> regions = states.stream().map(s -> new RegionView(s, null)).toList();
        map.put(key(code), regions);
        map.put(key(name), regions);
    }

    private static List<String> fallbackCities(String stateName) {
        return FALLBACK_CITIES.getOrDefault(stateName, List.of());
    }

    private static final Map<String, List<String>> FALLBACK_CITIES = Map.ofEntries(
            Map.entry("Punjab", List.of("Lahore", "Faisalabad", "Rawalpindi", "Multan", "Gujranwala", "Sialkot", "Bahawalpur")),
            Map.entry("Sindh", List.of("Karachi", "Hyderabad", "Sukkur", "Larkana")),
            Map.entry("Khyber Pakhtunkhwa", List.of("Peshawar", "Mardan", "Abbottabad", "Swat")),
            Map.entry("Balochistan", List.of("Quetta", "Gwadar", "Turbat")),
            Map.entry("Islamabad Capital Territory", List.of("Islamabad")),
            Map.entry("Gilgit-Baltistan", List.of("Gilgit", "Skardu")),
            Map.entry("Azad Kashmir", List.of("Muzaffarabad", "Mirpur")),
            Map.entry("Dubai", List.of("Dubai")),
            Map.entry("Abu Dhabi", List.of("Abu Dhabi", "Al Ain")),
            Map.entry("Sharjah", List.of("Sharjah")),
            Map.entry("Riyadh", List.of("Riyadh")),
            Map.entry("Makkah", List.of("Jeddah", "Mecca", "Taif")),
            Map.entry("California", List.of("Los Angeles", "San Francisco", "San Diego", "Sacramento")),
            Map.entry("New York", List.of("New York City", "Buffalo", "Rochester", "Albany")),
            Map.entry("Texas", List.of("Houston", "Austin", "Dallas", "San Antonio")),
            Map.entry("England", List.of("London", "Manchester", "Birmingham", "Leeds")),
            Map.entry("Maharashtra", List.of("Mumbai", "Pune", "Nagpur")),
            Map.entry("Delhi", List.of("New Delhi")),
            Map.entry("Ontario", List.of("Toronto", "Ottawa", "Mississauga")),
            Map.entry("New South Wales", List.of("Sydney", "Newcastle")),
            Map.entry("Victoria", List.of("Melbourne", "Geelong"))
    );

    private record CachedCities(List<String> cities, long cachedAt) {
        boolean expired() {
            return System.currentTimeMillis() - cachedAt > CITY_TTL_MS;
        }
    }
}
