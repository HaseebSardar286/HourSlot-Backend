package com.duemet.request.services;

import com.duemet.identity.model.CustomerAddress;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.CustomerAddressRepository;
import com.duemet.organization.services.FeatureFlagService;
import com.duemet.request.model.ServiceRequest;
import com.duemet.request.model.ServiceRequestMedia;
import com.duemet.request.repository.ServiceRequestMediaRepository;
import com.duemet.request.repository.ServiceRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class ServiceRequestService {
    private final ServiceRequestRepository repository;
    private final ServiceRequestMediaRepository mediaRepository;
    private final CustomerAddressRepository addressRepository;
    private final FeatureFlagService featureFlagService;
    private final RequestMatchingService matchingService;

    public ServiceRequestService(
            ServiceRequestRepository repository,
            ServiceRequestMediaRepository mediaRepository,
            CustomerAddressRepository addressRepository,
            FeatureFlagService featureFlagService,
            RequestMatchingService matchingService) {
        this.repository = repository;
        this.mediaRepository = mediaRepository;
        this.addressRepository = addressRepository;
        this.featureFlagService = featureFlagService;
        this.matchingService = matchingService;
    }

    @Transactional
    public ServiceRequest create(User customer, ServiceRequest request, List<String> mediaUrls) {
        requireEnabled();
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("Title is required.");
        }
        request.setId(null);
        request.setCustomerUserId(customer.getId());
        request.setStatus("OPEN");
        if (request.getUrgency() != null) {
            request.setUrgency(request.getUrgency().trim().toUpperCase(Locale.ROOT));
        }
        if (request.getCustomerAddressId() != null) {
            CustomerAddress address = addressRepository.findByIdAndUser(request.getCustomerAddressId(), customer)
                    .orElseThrow(() -> new IllegalArgumentException("Customer address not found."));
            request.setCountryCode(address.getCountryCode());
            request.setRegion(address.getRegion());
            request.setCity(address.getCity());
            request.setAreaName(address.getAreaName());
            request.setGeoAreaId(address.getGeoArea() == null ? null : address.getGeoArea().getId());
            request.setLatitude(address.getLatitude());
            request.setLongitude(address.getLongitude());
        }
        ServiceRequest saved = repository.save(request);
        if (mediaUrls != null) {
            int order = 0;
            for (String url : mediaUrls) {
                if (url == null || url.isBlank()) continue;
                mediaRepository.save(ServiceRequestMedia.builder()
                        .requestId(saved.getId())
                        .url(url.trim())
                        .storageKey("request/" + saved.getId() + "/" + order)
                        .mimeType("image/*")
                        .sortOrder(order++)
                        .build());
            }
        }
        var invited = matchingService.match(saved);
        if (invited.isEmpty()) {
            invited = matchingService.match(saved, true);
        }
        saved.setStatus(invited.isEmpty() ? "OPEN" : "MATCHING");
        return repository.save(saved);
    }

    @Transactional(readOnly = true)
    public List<ServiceRequest> listForCustomer(User customer) {
        return repository.findByCustomer(customer.getId());
    }

    @Transactional(readOnly = true)
    public ServiceRequest requireOwned(User customer, Long id) {
        ServiceRequest request = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service request not found."));
        if (!customer.getId().equals(request.getCustomerUserId())) {
            throw new SecurityException("Not allowed to access this service request.");
        }
        return request;
    }

    @Transactional
    public ServiceRequest cancel(User customer, Long id) {
        ServiceRequest request = requireOwned(customer, id);
        String status = request.getStatus() == null ? "" : request.getStatus().toUpperCase();
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)
                || "SCHEDULED".equals(status) || "ACCEPTED".equals(status)) {
            throw new IllegalStateException(
                    "Request cannot be cancelled in status " + request.getStatus()
                            + ". Cancel the booking or job instead if work was already scheduled.");
        }
        request.setStatus("CANCELLED");
        return repository.save(request);
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestMedia> media(Long requestId) {
        return mediaRepository.findByRequest(requestId);
    }

    public void requireEnabled() {
        if (!featureFlagService.isEnabled("service_requests")) {
            throw new IllegalStateException("Service requests are not currently available.");
        }
    }
}
