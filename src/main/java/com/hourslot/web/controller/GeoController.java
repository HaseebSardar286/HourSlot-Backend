package com.hourslot.web.controller;

import com.hourslot.geo.dto.CountryView;
import com.hourslot.geo.dto.CurrencyView;
import com.hourslot.geo.services.GeoCatalogService;
import com.hourslot.geo.repository.GeoAreaRepository;
import com.hourslot.organization.services.SystemSettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/geo")
public class GeoController {

    private final GeoCatalogService geoCatalogService;
    private final SystemSettingService systemSettingService;
    private final GeoAreaRepository geoAreaRepository;

    public GeoController(
            GeoCatalogService geoCatalogService,
            SystemSettingService systemSettingService,
            GeoAreaRepository geoAreaRepository) {
        this.geoCatalogService = geoCatalogService;
        this.systemSettingService = systemSettingService;
        this.geoAreaRepository = geoAreaRepository;
    }

    @GetMapping("/countries")
    public ResponseEntity<List<CountryView>> countries() {
        return ResponseEntity.ok(geoCatalogService.listCountries());
    }

    @GetMapping("/currencies")
    public ResponseEntity<List<CurrencyView>> currencies(
            @RequestParam(defaultValue = "false") boolean supportedOnly) {
        List<CurrencyView> all = geoCatalogService.listCurrencies();
        if (!supportedOnly) {
            return ResponseEntity.ok(all);
        }
        Set<String> allowed = supportedCurrencyCodes();
        if (allowed.isEmpty()) {
            return ResponseEntity.ok(all);
        }
        List<CurrencyView> filtered = all.stream()
                .filter(currency -> allowed.contains(currency.getCode()))
                .toList();
        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/states")
    public ResponseEntity<?> states(@RequestParam String country) {
        return ResponseEntity.ok(geoCatalogService.listStates(country));
    }

    @GetMapping("/cities")
    public ResponseEntity<?> cities(@RequestParam String country, @RequestParam String state) {
        return ResponseEntity.ok(geoCatalogService.listCities(country, state));
    }

    @GetMapping("/areas")
    public ResponseEntity<?> areas(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String city) {
        return ResponseEntity.ok(geoAreaRepository.findActiveByCity(country, city));
    }

    @GetMapping("/timezones")
    public ResponseEntity<?> timezones(@RequestParam(required = false) String country) {
        return ResponseEntity.ok(geoCatalogService.listTimezones(country));
    }

    @GetMapping("/supported-currencies")
    public ResponseEntity<Map<String, Object>> supportedCurrencies() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("defaultCurrency", systemSettingService.defaultCurrency());
        body.put("supportedCurrencies", systemSettingService.supportedCurrencyCodes());
        body.put("catalog", geoCatalogService.listCurrencies());
        return ResponseEntity.ok(body);
    }

    private Set<String> supportedCurrencyCodes() {
        return Arrays.stream(systemSettingService.supportedCurrencyCodes().split(","))
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .filter(code -> !code.isBlank())
                .collect(Collectors.toSet());
    }
}
