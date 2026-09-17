package com.hourslot.identity.services;

import com.hourslot.geo.repository.GeoAreaRepository;
import com.hourslot.identity.model.CustomerAddress;
import com.hourslot.identity.model.User;
import com.hourslot.identity.repository.CustomerAddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class CustomerAddressService {
    private final CustomerAddressRepository repository;
    private final GeoAreaRepository geoAreaRepository;

    public CustomerAddressService(CustomerAddressRepository repository, GeoAreaRepository geoAreaRepository) {
        this.repository = repository;
        this.geoAreaRepository = geoAreaRepository;
    }

    @Transactional(readOnly = true)
    public List<CustomerAddress> list(User user) {
        List<CustomerAddress> addresses = repository.findByUser(user);
        addresses.forEach(this::hydrateGeoArea);
        return addresses;
    }

    @Transactional
    public CustomerAddress create(User user, CustomerAddress address) {
        validate(address);
        address.setId(null);
        address.setCustomerUser(user);
        if (address.isDefault() || repository.findByUser(user).isEmpty()) {
            repository.clearDefault(user);
            address.setDefault(true);
        }
        CustomerAddress saved = repository.save(address);
        hydrateGeoArea(saved);
        return saved;
    }

    @Transactional
    public CustomerAddress update(User user, Long id, CustomerAddress changes) {
        CustomerAddress address = repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Address not found."));
        address.setLabel(changes.getLabel());
        address.setAddressLine(changes.getAddressLine());
        address.setCountryCode(changes.getCountryCode());
        address.setRegion(changes.getRegion());
        address.setCity(changes.getCity());
        address.setAreaName(changes.getAreaName());
        address.setPostalCode(changes.getPostalCode());
        address.setGeoArea(changes.getGeoArea());
        address.setLatitude(changes.getLatitude());
        address.setLongitude(changes.getLongitude());
        address.setDefault(changes.isDefault());
        validate(address);
        if (address.isDefault()) {
            repository.clearDefault(user);
        }
        CustomerAddress saved = repository.save(address);
        hydrateGeoArea(saved);
        return saved;
    }

    @Transactional
    public void delete(User user, Long id) {
        CustomerAddress address = repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Address not found."));
        boolean wasDefault = address.isDefault();
        repository.delete(address);
        if (wasDefault) {
            List<CustomerAddress> remaining = repository.findByUser(user);
            if (!remaining.isEmpty()) {
                CustomerAddress next = remaining.get(0);
                repository.clearDefault(user);
                next.setDefault(true);
                repository.save(next);
            }
        }
    }

    private void validate(CustomerAddress address) {
        if (address.getLabel() == null || address.getLabel().isBlank()) {
            throw new IllegalArgumentException("Address label is required.");
        }
        if (address.getAddressLine() == null || address.getAddressLine().isBlank()) {
            throw new IllegalArgumentException("Address line is required.");
        }
        if (address.getCountryCode() == null || address.getCountryCode().isBlank()) {
            throw new IllegalArgumentException("Country code is required.");
        }
        address.setCountryCode(address.getCountryCode().trim().toUpperCase(Locale.ROOT));
        if (address.getGeoArea() != null && address.getGeoArea().getId() != null) {
            address.setGeoArea(geoAreaRepository.findById(address.getGeoArea().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Geographic area not found.")));
        }
    }

    private void hydrateGeoArea(CustomerAddress address) {
        if (address.getGeoArea() != null && address.getGeoArea().getId() != null) {
            geoAreaRepository.findById(address.getGeoArea().getId()).ifPresent(address::setGeoArea);
        }
    }
}
