package com.hourslot.organization.services;

import com.hourslot.geo.model.GeoArea;
import com.hourslot.geo.repository.GeoAreaRepository;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.model.BusinessServiceArea;
import com.hourslot.organization.repository.BusinessServiceAreaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class ServiceAreaService {
    private final BusinessServiceAreaRepository repository;
    private final GeoAreaRepository geoAreaRepository;
    private final EntitlementService entitlementService;

    public ServiceAreaService(
            BusinessServiceAreaRepository repository,
            GeoAreaRepository geoAreaRepository,
            EntitlementService entitlementService) {
        this.repository = repository;
        this.geoAreaRepository = geoAreaRepository;
        this.entitlementService = entitlementService;
    }

    @Transactional(readOnly = true)
    public List<BusinessServiceArea> list(Business business) {
        List<BusinessServiceArea> areas = repository.findByBusiness(business);
        areas.forEach(this::hydrateGeoArea);
        return areas;
    }

    @Transactional
    public BusinessServiceArea create(Business business, BusinessServiceArea area) {
        entitlementService.requireFeature(
                business.getOrganization(), EntitlementService.HOME_SERVICE, "home service");
        entitlementService.requireHeadroom(
                business.getOrganization(), EntitlementService.MAX_SERVICE_AREAS,
                repository.countByBusinessId(business.getId()), "service areas");
        area.setId(null);
        area.setBusiness(business);
        validate(area);
        BusinessServiceArea saved = repository.save(area);
        hydrateGeoArea(saved);
        return saved;
    }

    @Transactional
    public BusinessServiceArea update(Business business, Long id, BusinessServiceArea changes) {
        entitlementService.requireFeature(
                business.getOrganization(), EntitlementService.HOME_SERVICE, "home service");
        BusinessServiceArea area = requireOwned(business, id);
        area.setCoverageType(changes.getCoverageType());
        area.setGeoArea(changes.getGeoArea());
        area.setAreaName(changes.getAreaName());
        area.setLatitude(changes.getLatitude());
        area.setLongitude(changes.getLongitude());
        area.setRadiusKm(changes.getRadiusKm());
        area.setStatus(changes.getStatus() == null ? "ACTIVE" : changes.getStatus());
        validate(area);
        BusinessServiceArea saved = repository.save(area);
        hydrateGeoArea(saved);
        return saved;
    }

    @Transactional
    public void delete(Business business, Long id) {
        repository.delete(requireOwned(business, id));
    }

    private BusinessServiceArea requireOwned(Business business, Long id) {
        BusinessServiceArea area = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service area not found."));
        if (area.getBusiness() == null || !business.getId().equals(area.getBusiness().getId())) {
            throw new SecurityException("Service area does not belong to this provider.");
        }
        area.setBusiness(business);
        return area;
    }

    private void validate(BusinessServiceArea area) {
        String type = area.getCoverageType() == null ? "" : area.getCoverageType().toUpperCase(Locale.ROOT);
        area.setCoverageType(type);
        if ("NAMED".equals(type)) {
            if (area.getGeoArea() == null || area.getGeoArea().getId() == null) {
                throw new IllegalArgumentException("geoAreaId is required for a NAMED service area.");
            }
            GeoArea geoArea = geoAreaRepository.findById(area.getGeoArea().getId())
                    .filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus()))
                    .orElseThrow(() -> new IllegalArgumentException("Active geographic area not found."));
            area.setGeoArea(geoArea);
            if (area.getAreaName() == null || area.getAreaName().isBlank()) {
                area.setAreaName(geoArea.getName());
            }
            area.setLatitude(null);
            area.setLongitude(null);
            area.setRadiusKm(null);
            return;
        }
        if ("RADIUS".equals(type)) {
            BigDecimal radius = area.getRadiusKm();
            if (area.getLatitude() == null || area.getLongitude() == null || radius == null
                    || radius.signum() <= 0 || radius.compareTo(BigDecimal.valueOf(50)) > 0) {
                throw new IllegalArgumentException(
                        "RADIUS service areas require latitude, longitude, and radiusKm greater than 0 and at most 50.");
            }
            area.setGeoArea(null);
            return;
        }
        throw new IllegalArgumentException("coverageType must be NAMED or RADIUS.");
    }

    private void hydrateGeoArea(BusinessServiceArea area) {
        if (area.getGeoArea() != null && area.getGeoArea().getId() != null) {
            geoAreaRepository.findById(area.getGeoArea().getId()).ifPresent(area::setGeoArea);
        }
    }
}
