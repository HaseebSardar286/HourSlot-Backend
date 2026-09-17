package com.hourslot.web.controller;

import com.hourslot.geo.model.GeoArea;
import com.hourslot.geo.repository.GeoAreaRepository;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/geo-areas")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminGeoController {
    private final GeoAreaRepository geoAreaRepository;

    public AdminGeoController(GeoAreaRepository geoAreaRepository) {
        this.geoAreaRepository = geoAreaRepository;
    }

    @Data
    public static class GeoAreaBody {
        private String countryCode;
        private String region;
        private String city;
        private String name;
        private String slug;
        private Double latitude;
        private Double longitude;
        private String status;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(geoAreaRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody GeoAreaBody body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("Name is required.");
        }
        return ResponseEntity.ok(geoAreaRepository.save(toArea(body, null)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody GeoAreaBody body) {
        GeoArea existing = geoAreaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Geo area not found."));
        return ResponseEntity.ok(geoAreaRepository.save(toArea(body, existing)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        geoAreaRepository.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    private static GeoArea toArea(GeoAreaBody body, GeoArea existing) {
        GeoArea area = existing == null ? new GeoArea() : existing;
        if (body.getCountryCode() != null) area.setCountryCode(body.getCountryCode());
        if (body.getRegion() != null) area.setRegion(body.getRegion());
        if (body.getCity() != null) area.setCity(body.getCity());
        if (body.getName() != null) area.setName(body.getName());
        if (body.getSlug() != null) area.setSlug(body.getSlug());
        if (body.getLatitude() != null) area.setLatitude(body.getLatitude());
        if (body.getLongitude() != null) area.setLongitude(body.getLongitude());
        if (body.getStatus() != null) area.setStatus(body.getStatus());
        return area;
    }
}
