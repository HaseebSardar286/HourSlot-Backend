package com.hourslot.request.services;

import com.hourslot.geo.model.GeoArea;
import com.hourslot.geo.repository.GeoAreaRepository;
import com.hourslot.geo.services.GeoCoverageMatcher;
import com.hourslot.geo.services.GeoCoverageMatcher.BranchLocation;
import com.hourslot.geo.services.GeoCoverageMatcher.CoverageArea;
import com.hourslot.geo.services.GeoCoverageMatcher.MatchDecision;
import com.hourslot.geo.services.GeoCoverageMatcher.RequestLocation;
import com.hourslot.identity.model.User;
import com.hourslot.identity.repository.UserRepository;
import com.hourslot.notification.services.NotificationService;
import com.hourslot.organization.model.Branch;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.model.BusinessServiceArea;
import com.hourslot.organization.model.OrganizationMember;
import com.hourslot.organization.repository.BranchRepository;
import com.hourslot.organization.repository.BusinessRepository;
import com.hourslot.organization.repository.BusinessServiceAreaRepository;
import com.hourslot.organization.repository.OrganizationMemberRepository;
import com.hourslot.organization.services.UsageCounterService;
import com.hourslot.request.model.ServiceRequest;
import com.hourslot.request.model.ServiceRequestProvider;
import com.hourslot.request.repository.ServiceRequestProviderRepository;
import com.hourslot.request.repository.ServiceRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RequestMatchingService {
    private final BusinessRepository businessRepository;
    private final BusinessServiceAreaRepository serviceAreaRepository;
    private final BranchRepository branchRepository;
    private final ServiceRequestProviderRepository providerLinkRepository;
    private final ServiceRequestRepository requestRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final GeoAreaRepository geoAreaRepository;
    private final UsageCounterService usageCounterService;

    public RequestMatchingService(
            BusinessRepository businessRepository,
            BusinessServiceAreaRepository serviceAreaRepository,
            BranchRepository branchRepository,
            ServiceRequestProviderRepository providerLinkRepository,
            ServiceRequestRepository requestRepository,
            OrganizationMemberRepository organizationMemberRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            UsageCounterService usageCounterService,
            GeoAreaRepository geoAreaRepository) {
        this.businessRepository = businessRepository;
        this.serviceAreaRepository = serviceAreaRepository;
        this.branchRepository = branchRepository;
        this.providerLinkRepository = providerLinkRepository;
        this.requestRepository = requestRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.usageCounterService = usageCounterService;
        this.geoAreaRepository = geoAreaRepository;
    }

    @Transactional
    public List<ServiceRequestProvider> match(ServiceRequest request) {
        return match(request, false);
    }

    @Transactional
    public List<ServiceRequestProvider> match(ServiceRequest request, boolean expandSearch) {
        List<Business> candidates = businessRepository.findPublishedForMatching(
                request.getCategoryId(), request.getCity(), expandSearch);
        List<Long> ids = candidates.stream().map(Business::getId).toList();
        Map<Long, List<BusinessServiceArea>> areasByBusiness = serviceAreaRepository.findActiveByBusinessIds(ids)
                .stream()
                .collect(Collectors.groupingBy(area -> area.getBusiness().getId()));
        Map<Long, List<Branch>> branchesByBusiness = branchRepository.findByBusinessIds(ids)
                .stream()
                .collect(Collectors.groupingBy(branch -> branch.getBusiness() == null
                        ? null
                        : branch.getBusiness().getId()));

        Map<Long, GeoArea> geoById = geoAreaRepository.findByIds(areasByBusiness.values().stream()
                        .flatMap(List::stream)
                        .map(BusinessServiceArea::getGeoArea)
                        .filter(geo -> geo != null && geo.getId() != null)
                        .map(GeoArea::getId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(GeoArea::getId, geo -> geo));

        RequestLocation location = new RequestLocation(
                request.getGeoAreaId(),
                request.getCity(),
                request.getAreaName(),
                request.getLatitude(),
                request.getLongitude());

        List<ServiceRequestProvider> invited = new ArrayList<>();
        for (Business business : candidates) {
            if (providerLinkRepository.findByRequestAndProvider(request.getId(), business.getId()).isPresent()) {
                continue;
            }
            MatchDecision decision = GeoCoverageMatcher.evaluate(
                    business.getServiceMode(),
                    business.isVerified(),
                    business.getRatingAvg() == null ? null : business.getRatingAvg().doubleValue(),
                    toCoverage(areasByBusiness.getOrDefault(business.getId(), List.of()), geoById),
                    toBranches(branchesByBusiness.getOrDefault(business.getId(), List.of())),
                    location);
            if (!decision.eligible() && !expandSearch) {
                continue;
            }
            if (!decision.eligible() && expandSearch) {
                decision = new MatchDecision(true, java.math.BigDecimal.valueOf(20), "expanded_search", null);
            }
            if ("BUSY".equalsIgnoreCase(business.getOpsStatus())) {
                decision = new MatchDecision(
                        true,
                        decision.score().subtract(java.math.BigDecimal.valueOf(15)).max(java.math.BigDecimal.ONE),
                        decision.reason() + "_busy",
                        decision.distanceKm());
            }
            LocalDateTime now = LocalDateTime.now();
            ServiceRequestProvider link = providerLinkRepository.save(ServiceRequestProvider.builder()
                    .requestId(request.getId())
                    .providerId(business.getId())
                    .matchScore(decision.score())
                    .matchReason(decision.reason())
                    .responseStatus("INVITED")
                    .sentAt(now)
                    .build());
            invited.add(link);
            if (business.getOrganization() != null) {
                usageCounterService.increment(business.getOrganization(), "requests_received");
            }
            notifyProviderOwners(business, request);
        }
        return invited;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> inboxForProvider(Business provider) {
        List<ServiceRequestProvider> links = providerLinkRepository.findByProvider(provider.getId());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ServiceRequestProvider link : links) {
            requestRepository.findById(link.getRequestId()).ifPresent(request -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("link", link);
                row.put("request", request);
                rows.add(row);
            });
        }
        return rows;
    }

    @Transactional
    public List<Map<String, Object>> inboxForProviderMarkViewed(Business provider) {
        List<Map<String, Object>> rows = inboxForProvider(provider);
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> row : rows) {
            Object linkObj = row.get("link");
            if (!(linkObj instanceof ServiceRequestProvider link)) {
                continue;
            }
            if (link.getViewedAt() == null && "INVITED".equalsIgnoreCase(link.getResponseStatus())) {
                link.setViewedAt(now);
                link.setResponseStatus("VIEWED");
                providerLinkRepository.save(link);
            }
        }
        return rows;
    }

    @Transactional
    public ServiceRequestProvider respond(Business provider, Long requestId, String status) {
        ServiceRequestProvider link = providerLinkRepository.findByRequestAndProvider(requestId, provider.getId())
                .orElseThrow(() -> new RuntimeException("Request invitation not found."));
        LocalDateTime now = LocalDateTime.now();
        if (link.getViewedAt() == null) {
            link.setViewedAt(now);
        }
        link.setResponseStatus(status.toUpperCase(Locale.ROOT));
        link.setRespondedAt(now);
        LocalDateTime start = link.getSentAt() != null ? link.getSentAt() : link.getCreatedAt();
        if (start != null) {
            link.setResponseMinutes((int) Math.max(0, Duration.between(start, now).toMinutes()));
        }
        return providerLinkRepository.save(link);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> providersForRequest(Long requestId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ServiceRequestProvider link : providerLinkRepository.findByRequest(requestId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", link.getId());
            row.put("providerId", link.getProviderId());
            row.put("matchScore", link.getMatchScore());
            row.put("matchReason", link.getMatchReason());
            row.put("responseStatus", link.getResponseStatus());
            businessRepository.findById(link.getProviderId()).ifPresent(business -> {
                row.put("name", business.getName());
                row.put("slug", business.getSlug());
                row.put("verified", business.isVerified());
            });
            rows.add(row);
        }
        return rows;
    }

    public void notifyCustomer(User customer, String title, String message) {
        notificationService.notify(customer, title, message);
    }

    private void notifyProviderOwners(Business business, ServiceRequest request) {
        if (business.getOrganization() == null || business.getOrganization().getId() == null) {
            return;
        }
        List<OrganizationMember> members =
                organizationMemberRepository.findByOrganizationAndStatus(business.getOrganization(), "ACTIVE");
        for (OrganizationMember member : members) {
            if (member.getUser() == null || member.getUser().getId() == null) {
                continue;
            }
            userRepository.findById(member.getUser().getId()).ifPresent(user ->
                    notificationService.notify(
                            user,
                            "REQUEST_MATCHED",
                            "New service request",
                            "A customer requested: " + request.getTitle(),
                            request.getId()));
        }
    }

    private static List<CoverageArea> toCoverage(List<BusinessServiceArea> areas, Map<Long, GeoArea> geoById) {
        List<CoverageArea> rows = new ArrayList<>();
        for (BusinessServiceArea area : areas) {
            GeoArea geo = area.getGeoArea();
            if (geo != null && geo.getId() != null) {
                geo = geoById.getOrDefault(geo.getId(), geo);
            }
            rows.add(new CoverageArea(
                    area.getCoverageType(),
                    geo == null ? null : geo.getId(),
                    area.getAreaName() != null ? area.getAreaName() : (geo == null ? null : geo.getName()),
                    geo == null ? null : geo.getCity(),
                    area.getLatitude() != null ? area.getLatitude() : (geo == null ? null : geo.getLatitude()),
                    area.getLongitude() != null ? area.getLongitude() : (geo == null ? null : geo.getLongitude()),
                    area.getRadiusKm()));
        }
        return rows;
    }

    private static List<BranchLocation> toBranches(List<Branch> branches) {
        List<BranchLocation> rows = new ArrayList<>();
        for (Branch branch : branches) {
            if (branch == null) {
                continue;
            }
            rows.add(new BranchLocation(branch.getCity(), branch.getLatitude(), branch.getLongitude()));
        }
        return rows;
    }
}
