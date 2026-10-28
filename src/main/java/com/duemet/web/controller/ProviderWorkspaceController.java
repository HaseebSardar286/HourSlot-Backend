package com.duemet.web.controller;

import com.duemet.availability.model.BranchWorkingHour;
import com.duemet.availability.repository.BranchWorkingHourRepository;
import com.duemet.catalog.model.Service;
import com.duemet.catalog.repository.ServiceRepository;
import com.duemet.identity.dto.MessageResponse;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.identity.security.CustomUserDetails;
import com.duemet.media.services.MediaAssetService;
import com.duemet.media.services.ObjectStorageService;
import com.duemet.organization.model.Branch;
import com.duemet.organization.model.Business;
import com.duemet.organization.model.BusinessVerificationDocument;
import com.duemet.organization.model.Organization;
import com.duemet.organization.repository.BranchRepository;
import com.duemet.organization.services.EntitlementService;
import com.duemet.organization.services.ProviderOpsService;
import com.duemet.organization.services.ServiceAreaService;
import com.duemet.organization.services.TenancyService;
import com.duemet.organization.services.VerificationDocumentService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Solo-provider workspace APIs (services, hours, bookings, plan, verification, media).
 * Individual listings must not use /api/business/**.
 */
@RestController
@RequestMapping("/api/provider")
public class ProviderWorkspaceController {

    private final UserRepository userRepository;
    private final TenancyService tenancyService;
    private final ProviderOpsService providerOpsService;
    private final ServiceRepository serviceRepository;
    private final ServiceAreaService serviceAreaService;
    private final BranchRepository branchRepository;
    private final BranchWorkingHourRepository workingHourRepository;
    private final EntitlementService entitlementService;
    private final VerificationDocumentService verificationDocumentService;
    private final MediaAssetService mediaAssetService;
    private final ObjectStorageService objectStorageService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public ProviderWorkspaceController(
            UserRepository userRepository,
            TenancyService tenancyService,
            ProviderOpsService providerOpsService,
            ServiceRepository serviceRepository,
            ServiceAreaService serviceAreaService,
            BranchRepository branchRepository,
            BranchWorkingHourRepository workingHourRepository,
            EntitlementService entitlementService,
            VerificationDocumentService verificationDocumentService,
            MediaAssetService mediaAssetService,
            ObjectStorageService objectStorageService) {
        this.userRepository = userRepository;
        this.tenancyService = tenancyService;
        this.providerOpsService = providerOpsService;
        this.serviceRepository = serviceRepository;
        this.serviceAreaService = serviceAreaService;
        this.branchRepository = branchRepository;
        this.workingHourRepository = workingHourRepository;
        this.entitlementService = entitlementService;
        this.verificationDocumentService = verificationDocumentService;
        this.mediaAssetService = mediaAssetService;
        this.objectStorageService = objectStorageService;
    }

    @Data
    public static class ServiceBody {
        private String name;
        private String description;
        private Double price;
        private String currency;
        private Integer durationMinutes;
        private Integer bufferMinutes;
        private Boolean active;
        private String serviceMode;
        private String pricingType;
        private Boolean requiresQuote;
        private Boolean allowsHomeService;
        private Double minimumPrice;
        private Double maximumPrice;
        private Integer estimatedDurationMinutes;
        /** Home visits: promised arrival window in minutes (0 = exact time). */
        private Integer arrivalWindowMinutes;
        /** Urgent bookings: surcharge as a percent of the price. */
        private Double urgentSurchargePercent;
    }

    private static void applyTradeSettings(Service service, ServiceBody request) {
        if (request.getArrivalWindowMinutes() != null) {
            if (request.getArrivalWindowMinutes() < 0 || request.getArrivalWindowMinutes() > 480) {
                throw new IllegalArgumentException("Arrival window must be between 0 and 480 minutes.");
            }
            service.setArrivalWindowMinutes(request.getArrivalWindowMinutes());
        }
        if (request.getUrgentSurchargePercent() != null) {
            if (request.getUrgentSurchargePercent() < 0 || request.getUrgentSurchargePercent() > 200) {
                throw new IllegalArgumentException("Urgent fee must be between 0% and 200%.");
            }
            service.setUrgentSurchargePercent(BigDecimal.valueOf(request.getUrgentSurchargePercent()));
        }
    }

    @Data
    public static class WorkingHourBody {
        private Integer dayOfWeek;
        private String startTime;
        private String endTime;
        private Boolean closed;
        private Integer slotStepMinutes;
    }

    @GetMapping("/workspace")
    public ResponseEntity<?> workspace(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = requireIndividual(userDetails);
        int services = serviceRepository.findByBusiness(business).size();
        int areas = serviceAreaService.list(business).size();
        Map<String, Object> readiness = verificationDocumentService.readiness(business);
        boolean ready = Boolean.TRUE.equals(readiness.get("ready"))
                || Boolean.TRUE.equals(readiness.get("tier1Complete"))
                || Boolean.TRUE.equals(readiness.get("complete"));
        return ResponseEntity.ok(providerOpsService.workspace(business, services, areas, ready));
    }

    @GetMapping("/plan")
    public ResponseEntity<?> plan(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = requireIndividual(userDetails);
        Organization organization = business.getOrganization();
        if (organization == null) {
            organization = tenancyService.requireOrganizationForUser(requireUser(userDetails));
        }
        return ResponseEntity.ok(entitlementService.snapshot(organization));
    }

    @GetMapping("/services")
    public ResponseEntity<?> listServices(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(serviceRepository.findByBusiness(requireIndividual(userDetails)));
    }

    @PostMapping("/services")
    @Transactional
    public ResponseEntity<?> createService(
            @RequestBody ServiceBody request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = requireIndividual(userDetails);
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Service name is required.");
        }
        double price = request.getPrice() == null ? 0 : request.getPrice();
        Service service = Service.builder()
                .business(business)
                .name(request.getName().trim())
                .description(request.getDescription())
                .basePrice(BigDecimal.valueOf(price))
                .currency(blank(request.getCurrency(), orgCurrency(business)))
                .durationMinutes(request.getDurationMinutes() == null ? 60 : request.getDurationMinutes())
                .bufferMinutes(request.getBufferMinutes() == null ? 0 : request.getBufferMinutes())
                .active(request.getActive() == null || request.getActive())
                .serviceMode(blank(request.getServiceMode(), business.getServiceMode() == null ? "CUSTOMER_LOCATION" : business.getServiceMode()))
                .pricingType(blank(request.getPricingType(), "FIXED").toUpperCase())
                .requiresQuote(Boolean.TRUE.equals(request.getRequiresQuote())
                        || "QUOTE".equalsIgnoreCase(request.getPricingType()))
                .allowsHomeService(request.getAllowsHomeService() == null || request.getAllowsHomeService())
                .minimumPrice(request.getMinimumPrice() == null ? null : BigDecimal.valueOf(request.getMinimumPrice()))
                .maximumPrice(request.getMaximumPrice() == null ? null : BigDecimal.valueOf(request.getMaximumPrice()))
                .estimatedDurationMinutes(request.getEstimatedDurationMinutes())
                .build();
        applyTradeSettings(service, request);
        serviceRepository.save(service);
        return ResponseEntity.ok(service);
    }

    @PutMapping("/services/{id}")
    @Transactional
    public ResponseEntity<?> updateService(
            @PathVariable Long id,
            @RequestBody ServiceBody request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = requireIndividual(userDetails);
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found."));
        if (service.getBusiness() == null || !service.getBusiness().getId().equals(business.getId())) {
            throw new IllegalStateException("Unauthorized service access.");
        }
        if (request.getName() != null) service.setName(request.getName().trim());
        if (request.getDescription() != null) service.setDescription(request.getDescription());
        if (request.getPrice() != null) service.setPrice(request.getPrice());
        if (request.getCurrency() != null) service.setCurrency(request.getCurrency());
        if (request.getDurationMinutes() != null) service.setDurationMinutes(request.getDurationMinutes());
        if (request.getBufferMinutes() != null) service.setBufferMinutes(request.getBufferMinutes());
        if (request.getActive() != null) service.setActive(request.getActive());
        if (request.getServiceMode() != null) service.setServiceMode(request.getServiceMode());
        if (request.getPricingType() != null) service.setPricingType(request.getPricingType().trim().toUpperCase());
        if (request.getRequiresQuote() != null) service.setRequiresQuote(request.getRequiresQuote());
        if ("QUOTE".equalsIgnoreCase(service.getPricingType())) service.setRequiresQuote(true);
        if (request.getAllowsHomeService() != null) service.setAllowsHomeService(request.getAllowsHomeService());
        if (request.getMinimumPrice() != null) service.setMinimumPrice(BigDecimal.valueOf(request.getMinimumPrice()));
        if (request.getMaximumPrice() != null) service.setMaximumPrice(BigDecimal.valueOf(request.getMaximumPrice()));
        applyTradeSettings(service, request);
        if (request.getEstimatedDurationMinutes() != null) {
            service.setEstimatedDurationMinutes(request.getEstimatedDurationMinutes());
        }
        return ResponseEntity.ok(serviceRepository.save(service));
    }

    @DeleteMapping("/services/{id}")
    @Transactional
    public ResponseEntity<?> deleteService(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = requireIndividual(userDetails);
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found."));
        if (service.getBusiness() == null || !service.getBusiness().getId().equals(business.getId())) {
            throw new IllegalStateException("Unauthorized service access.");
        }
        serviceRepository.delete(service);
        return ResponseEntity.ok(new MessageResponse("Service deleted."));
    }

    @GetMapping("/availability")
    public ResponseEntity<?> availability(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = requireIndividual(userDetails);
        Branch branch = requireImplicitBranch(business);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("branchId", branch.getId());
        body.put("branchName", branch.getName());
        body.put("workingHours", workingHourRepository.findByBranchOrderByDayOfWeekAsc(branch));
        return ResponseEntity.ok(body);
    }

    @PutMapping("/availability")
    @Transactional
    public ResponseEntity<?> saveAvailability(
            @RequestBody List<WorkingHourBody> hours,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = requireIndividual(userDetails);
        Branch branch = requireImplicitBranch(business);
        List<BranchWorkingHour> existing = workingHourRepository.findByBranchOrderByDayOfWeekAsc(branch);
        for (BranchWorkingHour hour : existing) {
            workingHourRepository.delete(hour);
        }
        if (hours != null) {
            for (WorkingHourBody row : hours) {
                if (row.getDayOfWeek() == null) continue;
                BranchWorkingHour hour = BranchWorkingHour.builder()
                        .branch(branch)
                        .dayOfWeek(row.getDayOfWeek())
                        .startTime(parseTime(row.getStartTime(), LocalTime.of(9, 0)))
                        .endTime(parseTime(row.getEndTime(), LocalTime.of(17, 0)))
                        .closed(Boolean.TRUE.equals(row.getClosed()))
                        .slotStepMinutes(row.getSlotStepMinutes() == null ? 30 : row.getSlotStepMinutes())
                        .build();
                workingHourRepository.save(hour);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("branchId", branch.getId());
        body.put("workingHours", workingHourRepository.findByBranchOrderByDayOfWeekAsc(branch));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/verification-documents")
    public ResponseEntity<?> verificationDocs(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = requireIndividual(userDetails);
        List<Map<String, Object>> docs = verificationDocumentService.list(business).stream()
                .map(this::docView)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("documents", docs);
        body.put("readiness", verificationDocumentService.readiness(business));
        List<Map<String, Object>> tier1 = verificationDocumentService.tier1CatalogFor(business);
        List<Map<String, Object>> tier2 = verificationDocumentService.tier2Catalog();
        List<Map<String, Object>> all = new java.util.ArrayList<>(tier1);
        all.addAll(tier2);
        body.put("requiredTypes", all);
        body.put("tier1Types", tier1);
        body.put("tier2Types", tier2);
        return ResponseEntity.ok(body);
    }

    @PostMapping(value = "/verification-documents", consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadVerification(
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws Exception {
        Business business = requireIndividual(userDetails);
        BusinessVerificationDocument saved = verificationDocumentService.upload(business, documentType, file);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("document", docView(saved));
        body.put("readiness", verificationDocumentService.readiness(business));
        body.put("message", "Document uploaded for review.");
        return ResponseEntity.ok(body);
    }

    @PostMapping(value = "/media/upload", consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("File is empty."));
        }
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        if (!com.duemet.media.services.ImageUploadValidator.isAllowed(contentType)) {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    com.duemet.media.services.ImageUploadValidator.rejectionMessage()));
        }
        Business business = requireIndividual(userDetails);
        String ext = com.duemet.media.services.ImageUploadValidator.extensionFor(contentType);
        String publicUrl;
        if (objectStorageService.isConfigured()) {
            ObjectStorageService.StoredObject stored = objectStorageService.upload(
                    objectStorageService.profileFolder(),
                    "solo-" + business.getId(),
                    contentType,
                    file.getBytes(),
                    ext);
            publicUrl = stored.publicUrl();
        } else {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize()
                    .resolve(objectStorageService.profileFolder());
            Files.createDirectories(dir);
            String filename = "solo-" + business.getId() + "-" + UUID.randomUUID() + ext;
            Files.write(dir.resolve(filename), file.getBytes());
            publicUrl = "/uploads/" + objectStorageService.profileFolder() + "/" + filename;
        }
        String galleryUrls = mediaAssetService.appendGalleryUrl(business.getId(), publicUrl);
        return ResponseEntity.ok(Map.of("url", publicUrl, "galleryUrls", galleryUrls));
    }

    @DeleteMapping("/media/gallery")
    public ResponseEntity<?> removeGallery(
            @RequestParam String url,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = requireIndividual(userDetails);
        if (objectStorageService.isConfigured() && url != null && url.startsWith("http")) {
            objectStorageService.deleteByPublicUrl(url);
        }
        mediaAssetService.removeGalleryUrl(business.getId(), url);
        return ResponseEntity.ok(new MessageResponse("Image removed from gallery."));
    }

    @GetMapping("/branch")
    public ResponseEntity<?> implicitBranch(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Branch branch = requireImplicitBranch(requireIndividual(userDetails));
        return ResponseEntity.ok(branch);
    }

    private Business requireIndividual(CustomUserDetails userDetails) {
        return tenancyService.requireIndividualProvider(requireUser(userDetails));
    }

    private User requireUser(CustomUserDetails userDetails) {
        return userRepository.findById(userDetails.getId()).orElseThrow();
    }

    private Branch requireImplicitBranch(Business business) {
        List<Branch> branches = branchRepository.findByBusiness(business);
        return branches.stream()
                .filter(Branch::isImplicit)
                .findFirst()
                .or(() -> branches.stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("Service area branch not found for this listing."));
    }

    private Map<String, Object> docView(BusinessVerificationDocument doc) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", doc.getId());
        row.put("documentType", doc.getDocumentType());
        row.put("status", doc.getStatus());
        String fileUrl = doc.getMediaAsset() == null ? null : doc.getMediaAsset().getUrl();
        row.put("fileUrl", fileUrl);
        row.put("url", fileUrl);
        row.put("originalFilename", doc.getOriginalFilename());
        row.put("createdAt", doc.getCreatedAt());
        row.put("reviewedAt", doc.getReviewedAt());
        row.put("rejectionReason", doc.getReviewNotes());
        row.put("reviewNotes", doc.getReviewNotes());
        return row;
    }

    private static String orgCurrency(Business business) {
        if (business.getOrganization() != null && business.getOrganization().getDefaultCurrency() != null) {
            return business.getOrganization().getDefaultCurrency();
        }
        return "PKR";
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static LocalTime parseTime(String value, LocalTime fallback) {
        if (value == null || value.isBlank()) return fallback;
        return LocalTime.parse(value);
    }
}
