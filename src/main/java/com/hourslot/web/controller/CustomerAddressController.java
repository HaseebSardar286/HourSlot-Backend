package com.hourslot.web.controller;

import com.hourslot.geo.model.GeoArea;
import com.hourslot.identity.model.CustomerAddress;
import com.hourslot.identity.model.User;
import com.hourslot.identity.repository.UserRepository;
import com.hourslot.identity.security.CustomUserDetails;
import com.hourslot.identity.services.CustomerAddressService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile/addresses")
public class CustomerAddressController {
    private final CustomerAddressService service;
    private final UserRepository userRepository;

    public CustomerAddressController(CustomerAddressService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    @Data
    public static class AddressRequest {
        private String label;
        private String addressLine;
        private String countryCode;
        private String region;
        private String city;
        private String areaName;
        private String postalCode;
        private Long geoAreaId;
        private Double latitude;
        private Double longitude;
        private boolean isDefault;
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(service.list(user(userDetails)));
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody AddressRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(service.create(user(userDetails), toAddress(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody AddressRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(service.update(user(userDetails), id, toAddress(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        service.delete(user(userDetails), id);
        return ResponseEntity.noContent().build();
    }

    private User user(CustomUserDetails details) {
        return userRepository.findById(details.getId()).orElseThrow();
    }

    private static CustomerAddress toAddress(AddressRequest request) {
        return CustomerAddress.builder()
                .label(request.getLabel())
                .addressLine(request.getAddressLine())
                .countryCode(request.getCountryCode())
                .region(request.getRegion())
                .city(request.getCity())
                .areaName(request.getAreaName())
                .postalCode(request.getPostalCode())
                .geoArea(request.getGeoAreaId() == null ? null
                        : GeoArea.builder().id(request.getGeoAreaId()).build())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .isDefault(request.isDefault())
                .build();
    }
}
