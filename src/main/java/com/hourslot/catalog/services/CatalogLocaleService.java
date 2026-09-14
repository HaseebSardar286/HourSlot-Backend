package com.hourslot.catalog.services;

import com.hourslot.shared.jdbc.JdbcSupport;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.model.Organization;
import com.hourslot.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;
import com.hourslot.organization.services.SystemSettingService;

/**
 * Resolves the operating currency for an organization and stamps it onto services/packages.
 */
@Service
public class CatalogLocaleService {

    private final OrganizationRepository organizationRepository;
    private final SystemSettingService systemSettingService;
    private final JdbcSupport jdbc;

    public CatalogLocaleService(
            OrganizationRepository organizationRepository,
            SystemSettingService systemSettingService,
            JdbcSupport jdbc) {
        this.organizationRepository = organizationRepository;
        this.systemSettingService = systemSettingService;
        this.jdbc = jdbc;
    }

    public String resolveCurrency(Organization organization) {
        Organization loaded = organization;
        if (loaded != null && loaded.getId() != null) {
            loaded = organizationRepository.findById(loaded.getId()).orElse(loaded);
        }
        if (loaded != null && loaded.getDefaultCurrency() != null && !loaded.getDefaultCurrency().isBlank()) {
            return loaded.getDefaultCurrency().trim().toUpperCase(Locale.ROOT);
        }
        return systemSettingService.defaultCurrency();
    }

    public String resolveCurrency(Business business) {
        if (business == null) {
            return systemSettingService.defaultCurrency();
        }
        return resolveCurrency(business.getOrganization());
    }

    public String normalizeCurrency(String currency, Business business) {
        if (currency != null && !currency.isBlank()) {
            return currency.trim().toUpperCase(Locale.ROOT);
        }
        return resolveCurrency(business);
    }

    public void applyCurrencyToCatalog(Long organizationId, String currency) {
        if (organizationId == null || currency == null || currency.isBlank()) {
            return;
        }
        String code = currency.trim().toUpperCase(Locale.ROOT);
        jdbc.update("""
                UPDATE services SET currency = :currency, updated_at = NOW()
                WHERE deleted_at IS NULL
                  AND business_id IN (SELECT id FROM businesses WHERE organization_id = :orgId AND deleted_at IS NULL)
                """, jdbc.params().addValue("currency", code).addValue("orgId", organizationId));
        jdbc.update("""
                UPDATE service_packages SET currency = :currency, updated_at = NOW()
                WHERE deleted_at IS NULL
                  AND business_id IN (SELECT id FROM businesses WHERE organization_id = :orgId AND deleted_at IS NULL)
                """, jdbc.params().addValue("currency", code).addValue("orgId", organizationId));
    }

    public void applyTimezoneToBusinesses(Long organizationId, String timezone, String countryCode) {
        if (organizationId == null) {
            return;
        }
        String locale = countryCode == null || countryCode.isBlank()
                ? null
                : "en-" + countryCode.trim().toUpperCase(Locale.ROOT);
        jdbc.update("""
                UPDATE businesses SET timezone = COALESCE(:timezone, timezone),
                    locale = COALESCE(:locale, locale), updated_at = NOW()
                WHERE organization_id = :orgId AND deleted_at IS NULL
                """, jdbc.params()
                .addValue("timezone", blankToNull(timezone))
                .addValue("locale", locale)
                .addValue("orgId", organizationId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
