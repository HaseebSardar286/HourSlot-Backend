package com.duemet.web.controller;

import com.duemet.booking.model.Booking;
import com.duemet.booking.model.BookingItem;
import com.duemet.booking.model.BookingStatus;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.catalog.model.Category;
import com.duemet.catalog.repository.CategoryRepository;
import com.duemet.catalog.repository.ServiceRepository;
import com.duemet.geo.repository.GeoAreaRepository;
import com.duemet.identity.model.CustomerProfile;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.CustomerProfileRepository;
import com.duemet.identity.repository.UserRepository;
import com.duemet.media.services.MediaAssetService;
import com.duemet.organization.model.Branch;
import com.duemet.organization.model.Business;
import com.duemet.organization.model.BusinessServiceArea;
import com.duemet.organization.model.BusinessStatus;
import com.duemet.organization.model.Organization;
import com.duemet.organization.model.Staff;
import com.duemet.organization.repository.BranchRepository;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.organization.repository.StaffRepository;
import com.duemet.organization.services.RbacService;
import com.duemet.organization.services.ServiceAreaService;
import com.duemet.organization.services.TenancyService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

@org.springframework.stereotype.Service
public class AdminSeedService {

    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final StaffRepository staffRepository;
    private final BookingRepository bookingRepository;
    private final CategoryRepository categoryRepository;
    private final GeoAreaRepository geoAreaRepository;
    private final ServiceAreaService serviceAreaService;
    private final TenancyService tenancyService;
    private final RbacService rbacService;
    private final MediaAssetService mediaAssetService;
    private final PasswordEncoder encoder;

    public AdminSeedService(
            UserRepository userRepository,
            CustomerProfileRepository customerProfileRepository,
            BusinessRepository businessRepository,
            BranchRepository branchRepository,
            ServiceRepository serviceRepository,
            StaffRepository staffRepository,
            BookingRepository bookingRepository,
            CategoryRepository categoryRepository,
            GeoAreaRepository geoAreaRepository,
            ServiceAreaService serviceAreaService,
            TenancyService tenancyService,
            RbacService rbacService,
            MediaAssetService mediaAssetService,
            PasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.serviceRepository = serviceRepository;
        this.staffRepository = staffRepository;
        this.bookingRepository = bookingRepository;
        this.categoryRepository = categoryRepository;
        this.geoAreaRepository = geoAreaRepository;
        this.serviceAreaService = serviceAreaService;
        this.tenancyService = tenancyService;
        this.rbacService = rbacService;
        this.mediaAssetService = mediaAssetService;
        this.encoder = encoder;
    }

    public boolean coreSeedAlreadyPresent() {
        return userRepository.findByEmail("alice@duemet.com").isPresent();
    }

    public int ensureDemoSoloProviders() {
        int created = 0;
        if (ensureSoloProvider(
                "demo.electrician@duemet.com", "Electrician", "Demo Electrician Lahore",
                "demo-electrician-lahore", resolveCategory("electrician", "home-services", "Electrician", "fa-bolt"),
                "Demo individual electrician covering named Lahore areas.",
                "Demo wiring estimate", "On-site assessment before quote.", 0, 60)) {
            created++;
        }
        if (ensureSoloProvider(
                "demo.cleaner@duemet.com", "Cleaner", "Demo Cleaner Gulberg",
                "demo-cleaner-gulberg", resolveCategory("cleaning", "home-services", "Cleaning", "fa-broom"),
                "Demo hybrid cleaner with radius coverage.",
                "Demo deep clean visit", "Bookable fixed-price home clean.", 4500, 120)) {
            created++;
        }
        if (ensureSoloProvider(
                "demo.plumber@duemet.com", "Plumber", "Demo Plumber Model Town",
                "demo-plumber-model-town", resolveCategory("plumbing", "home-services", "Plumbing", "fa-faucet"),
                "Demo on-site plumbing diagnosis and repair.",
                "Demo leak repair", "Fixed-price visit to stop a leaking tap or pipe joint.", 3200, 60)) {
            created++;
        }
        if (ensureSoloProvider(
                "demo.ac@duemet.com", "Technician", "Demo AC Repair Johar Town",
                "demo-ac-johar-town",
                resolveCategory("ac-appliance-repair", "home-services", "AC & Appliance Repair", "fa-snowflake"),
                "Demo AC service covering named Lahore areas.",
                "Demo AC gas top-up quote", "Quote visit before refrigerant work.", 0, 50)) {
            created++;
        }
        if (ensureSoloProvider(
                "demo.carpenter@duemet.com", "Carpenter", "Demo Carpenter DHA",
                "demo-carpenter-dha", resolveCategory("carpentry", "home-services", "Carpentry", "fa-hammer"),
                "Demo carpenter for furniture repair at the customer location.",
                "Demo door hinge repair", "Fixed-price hinge and latch repair visit.", 2800, 75)) {
            created++;
        }
        return created;
    }

    public SeedResult seedCoreFixtures() {
        User ownerUser = createSeedUser("zenithowner@duemet.com", "Robert", "Kowalski", "+15551212");
        User ownerUser2 = createSeedUser("elitedental@duemet.com", "Dr. Fatima", "Zahra", "+15551313");
        User staffUser = createSeedUser("johndoe@duemet.com", "John", "Doe", "+15551414");
        User custUser = createSeedUser("alice@duemet.com", "Alice", "Green", "+15550101");
        saveCustomerProfile(custUser, "101 Emerald St", "Female", LocalDate.of(1995, 8, 15));
        rbacService.grantSystemRole(custUser, "CUSTOMER", null, null, null, null);

        User custUser2 = createSeedUser("bob@duemet.com", "Bob", "Miller", "+15550102");
        saveCustomerProfile(custUser2, "202 Sapphire Rd", "Male", LocalDate.of(1990, 4, 20));
        rbacService.grantSystemRole(custUser2, "CUSTOMER", null, null, null, null);

        Category salonCat = resolveCategory("hair-salon", "beauty-salons", "Hair Salon", "fa-scissors");
        Category healthCat = resolveCategory("dentist", "health-medical", "Dentist", "fa-tooth");
        Category homeCat = resolveCategory("electrician", "home-services", "Electrician", "fa-bolt");

        Organization org1 = tenancyService.provisionOrganization(ownerUser, "Zenith Hair Salon");
        Organization org2 = tenancyService.provisionOrganization(ownerUser2, "Elite Dental Care");

        Business business1 = createSeedBusiness(
                "Zenith Hair Salon",
                org1,
                "Luxury haircut, colors, and styling services in downtown.",
                salonCat,
                BusinessStatus.APPROVED,
                true,
                "PUBLISHED",
                BigDecimal.valueOf(4.8));
        mediaAssetService.replaceLogo(
                business1.getId(), "https://images.unsplash.com/photo-1560066984-138dadb4c035?w=120");

        Business business2 = createSeedBusiness(
                "Elite Dental Care",
                org2,
                "Advanced dental clinic specializing in implants and cosmetic smile alignment.",
                healthCat,
                BusinessStatus.PENDING,
                false,
                "DRAFT",
                null);
        mediaAssetService.replaceLogo(
                business2.getId(), "https://images.unsplash.com/photo-1629909613654-28e377c37b09?w=120");

        Branch branch1 = createSeedBranch(
                business1, "Zenith Salon Downtown", "123 Main St, San Francisco, CA", "+15553030", 37.7749, -122.4194);
        createSeedBranch(
                business2, "Elite Dental Bay Area", "456 Medical Blvd, San Francisco, CA", "+15554040", 37.7849, -122.4094);

        com.duemet.catalog.model.Service service1 = createSeedService(
                business1, "Classic Haircut & Styling",
                "Includes hairwash, styling, and premium massage.",
                45.0, 45, "USD", "FIXED", false, "AT_PROVIDER", false, 0);
        com.duemet.catalog.model.Service service2 = createSeedService(
                business1, "Full Hair Coloring",
                "Professional highlights and base color treatment.",
                120.0, 90, "USD", "FIXED", false, "AT_PROVIDER", false, 0);
        createSeedService(
                business2, "Teeth Whitening",
                "Laser cleaning and whitening treatment.",
                180.0, 60, "USD", "FIXED", false, "AT_PROVIDER", false, 0);

        Staff staff1 = createSeedStaff(branch1, staffUser, "John Doe", "Master Barber", BigDecimal.valueOf(4.9));
        rbacService.grantSystemRole(staffUser, "STAFF", org1, business1, branch1, staff1);
        Staff staff2 = createSeedStaff(branch1, null, "Sarah Jenkins", "Senior Colorist", BigDecimal.valueOf(4.7));

        seedBooking(
                custUser, business1, branch1, service1, staff1,
                LocalDateTime.now().minusMonths(3).withHour(10).withMinute(0),
                45, BookingStatus.COMPLETED, 45.0);
        seedBooking(
                custUser2, business1, branch1, service2, staff2,
                LocalDateTime.now().minusMonths(2).withHour(14).withMinute(0),
                90, BookingStatus.COMPLETED, 120.0);
        seedBooking(
                custUser, business1, branch1, service1, staff1,
                LocalDateTime.now().plusDays(2).withHour(11).withMinute(0),
                45, BookingStatus.CONFIRMED, 45.0);

        User electricianOwner = createSeedUser(
                "demo.electrician@duemet.com", "Demo", "Electrician", "+923001111111");
        Business electrician = tenancyService.provisionIndividualProvider(
                electricianOwner, "Demo Electrician Lahore", "PK", "Punjab", "Lahore", "Asia/Karachi", "PKR");
        publishIndividual(
                electrician, homeCat,
                "Demo individual electrician covering named Lahore areas. Quote work is not slot-bookable.",
                "CUSTOMER_LOCATION", 8, "demo-electrician-lahore", 0);
        createSeedService(
                electrician, "Demo wiring estimate",
                "On-site assessment before quote. Requests arrive in Phase 2.",
                0, 60, "PKR", "QUOTE", true, "CUSTOMER_LOCATION", true, 0);
        createSeedService(
                electrician, "Demo outlet repair",
                "Fixed-price visit for a single outlet repair.",
                2500, 45, "PKR", "FIXED", false, "CUSTOMER_LOCATION", true, 0);
        attachNamedLahoreAreas(electrician);

        User cleanerOwner = createSeedUser("demo.cleaner@duemet.com", "Demo", "Cleaner", "+923002222222");
        Business cleaner = tenancyService.provisionIndividualProvider(
                cleanerOwner, "Demo Cleaner Gulberg", "PK", "Punjab", "Lahore", "Asia/Karachi", "PKR");
        publishIndividual(
                cleaner, homeCat,
                "Demo hybrid cleaner with radius coverage and a bookable fixed home visit.",
                "HYBRID", 5, "demo-cleaner-gulberg", 20);
        createSeedService(
                cleaner, "Demo deep clean visit",
                "Bookable fixed-price home clean on the implicit branch.",
                4500, 120, "PKR", "FIXED", false, "CUSTOMER_LOCATION", true, 20);
        attachRadiusArea(cleaner, "Demo Gulberg radius", 31.5102, 74.3441, 8.0);

        seedLahoreHomeDemo(
                "demo.plumber@duemet.com", "Plumber", "Demo Plumber Model Town",
                "demo-plumber-model-town", homeCat, "Demo on-site plumbing diagnosis and repair.",
                "Demo leak repair", "Fixed-price visit to stop a leaking tap or pipe joint.", 3200, 60);
        seedLahoreHomeDemo(
                "demo.ac@duemet.com", "Technician", "Demo AC Repair Johar Town",
                "demo-ac-johar-town", homeCat, "Demo AC service covering named Lahore areas.",
                "Demo AC gas top-up quote", "Quote visit before refrigerant work.", 0, 50);
        seedLahoreHomeDemo(
                "demo.carpenter@duemet.com", "Carpenter", "Demo Carpenter DHA",
                "demo-carpenter-dha", homeCat, "Demo carpenter for furniture repair at the customer location.",
                "Demo door hinge repair", "Fixed-price hinge and latch repair visit.", 2800, 75);

        return new SeedResult(business1.getId(), electrician.getId(), cleaner.getId());
    }

    public record SeedResult(Long businessId, Long electricianId, Long cleanerId) {
    }

    private boolean ensureSoloProvider(
            String email, String lastName, String displayName, String slug, Category category,
            String bio, String serviceName, String serviceDescription, double price, int durationMinutes) {
        if (userRepository.findByEmail(email).isPresent()) {
            return false;
        }
        seedLahoreHomeDemo(
                email, lastName, displayName, slug, category, bio, serviceName, serviceDescription, price, durationMinutes);
        return true;
    }

    private void seedLahoreHomeDemo(
            String email, String lastName, String displayName, String slug, Category category,
            String bio, String serviceName, String serviceDescription, double price, int durationMinutes) {
        User owner = createSeedUser(
                email, "Demo", lastName, "+92300" + Math.abs(email.hashCode() % 1000000));
        Business provider = tenancyService.provisionIndividualProvider(
                owner, displayName, "PK", "Punjab", "Lahore", "Asia/Karachi", "PKR");
        publishIndividual(provider, category, bio, "CUSTOMER_LOCATION", 6, slug, 0);

        boolean quoteOnly = price <= 0;
        createSeedService(
                provider, serviceName, serviceDescription, price, durationMinutes, "PKR",
                quoteOnly ? "QUOTE" : "FIXED", quoteOnly, "CUSTOMER_LOCATION", true, 0);
        attachNamedLahoreAreas(provider);
    }

    private Category resolveCategory(String preferredSlug, String fallbackSlug, String name, String icon) {
        Optional<Category> preferred = categoryRepository.findBySlug(preferredSlug);
        if (preferred.isPresent()) {
            return preferred.get();
        }
        Optional<Category> fallback = categoryRepository.findBySlug(fallbackSlug);
        if (fallback.isPresent()) {
            return fallback.get();
        }
        Category created = new Category();
        created.setName(name);
        created.setSlug(preferredSlug);
        created.setIcon(icon);
        created.setActive(true);
        return categoryRepository.save(created);
    }

    private User createSeedUser(String email, String firstName, String lastName, String phone) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode("password123"));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhoneNumber(phone);
        user.setStatus("ACTIVE");
        return userRepository.save(user);
    }

    private void saveCustomerProfile(User user, String addressLine1, String gender, LocalDate dateOfBirth) {
        CustomerProfile profile = new CustomerProfile();
        profile.setUser(user);
        profile.setAddressLine1(addressLine1);
        profile.setGender(gender);
        profile.setDateOfBirth(dateOfBirth);
        customerProfileRepository.save(profile);
    }

    private Business createSeedBusiness(
            String name,
            Organization organization,
            String description,
            Category category,
            BusinessStatus status,
            boolean verified,
            String publishStatus,
            BigDecimal ratingAvg) {
        Business business = new Business();
        business.setName(name);
        business.setOrganization(organization);
        business.setDescription(description);
        business.setPrimaryCategory(category);
        business.setStatus(status);
        business.setVerified(verified);
        business.setListingMode("BUSINESS");
        business.setProviderType("BUSINESS");
        business.setServiceMode("AT_PROVIDER");
        business.setPublishStatus(publishStatus);
        if (ratingAvg != null) {
            business.setRatingAvg(ratingAvg);
        }
        return businessRepository.save(business);
    }

    private void publishIndividual(
            Business business,
            Category category,
            String description,
            String serviceMode,
            int yearsExperience,
            String slug,
            int travelBufferMinutes) {
        business.setPrimaryCategory(category);
        business.setDescription(description);
        business.setServiceMode(serviceMode);
        business.setStatus(BusinessStatus.APPROVED);
        business.setPublishStatus("PUBLISHED");
        business.setOnboardingState("PUBLISHED");
        business.setYearsExperience(yearsExperience);
        business.setSlug(slug);
        if (travelBufferMinutes > 0) {
            business.setTravelBufferMinutes(travelBufferMinutes);
        }
        businessRepository.save(business);
    }

    private Branch createSeedBranch(
            Business business, String name, String address, String phone, double lat, double lon) {
        Branch branch = new Branch();
        branch.setBusiness(business);
        branch.setName(name);
        branch.setAddress(address);
        branch.setPhoneNumber(phone);
        branch.setLatitude(lat);
        branch.setLongitude(lon);
        return branchRepository.save(branch);
    }

    private com.duemet.catalog.model.Service createSeedService(
            Business business,
            String name,
            String description,
            double price,
            int durationMinutes,
            String currency,
            String pricingType,
            boolean requiresQuote,
            String serviceMode,
            boolean allowsHomeService,
            int bufferMinutes) {
        com.duemet.catalog.model.Service service = new com.duemet.catalog.model.Service();
        service.setBusiness(business);
        service.setName(name);
        service.setDescription(description);
        service.setBasePrice(BigDecimal.valueOf(price));
        service.setCurrency(currency);
        service.setDurationMinutes(durationMinutes);
        service.setPricingType(pricingType);
        service.setRequiresQuote(requiresQuote);
        service.setServiceMode(serviceMode);
        service.setAllowsHomeService(allowsHomeService);
        service.setBufferMinutes(bufferMinutes);
        return serviceRepository.save(service);
    }

    private Staff createSeedStaff(
            Branch branch, User user, String displayName, String designation, BigDecimal ratingAvg) {
        Staff staff = new Staff();
        staff.setBranch(branch);
        staff.setUser(user);
        staff.setDisplayName(displayName);
        staff.setDesignation(designation);
        staff.setRatingAvg(ratingAvg);
        return staffRepository.save(staff);
    }

    private void attachNamedLahoreAreas(Business business) {
        geoAreaRepository.findActiveByCity("PK", "Lahore").stream().limit(2).forEach(area -> {
            BusinessServiceArea coverage = new BusinessServiceArea();
            coverage.setCoverageType("NAMED");
            coverage.setGeoArea(area);
            coverage.setAreaName(area.getName());
            coverage.setStatus("ACTIVE");
            serviceAreaService.create(business, coverage);
        });
    }

    private void attachRadiusArea(
            Business business, String areaName, double lat, double lon, double radiusKm) {
        BusinessServiceArea coverage = new BusinessServiceArea();
        coverage.setCoverageType("RADIUS");
        coverage.setAreaName(areaName);
        coverage.setLatitude(lat);
        coverage.setLongitude(lon);
        coverage.setRadiusKm(BigDecimal.valueOf(radiusKm));
        coverage.setStatus("ACTIVE");
        serviceAreaService.create(business, coverage);
    }

    private void seedBooking(
            User customer, Business business, Branch branch, com.duemet.catalog.model.Service service, Staff staff,
            LocalDateTime start, int durationMinutes, BookingStatus status, double price) {
        Booking booking = new Booking();
        booking.setCustomerUser(customer);
        booking.setOrganization(business.getOrganization());
        booking.setBusiness(business);
        booking.setBranch(branch);
        booking.setBookingTime(start);
        booking.setEndTime(start.plusMinutes(durationMinutes));
        booking.setStatus(status);
        booking.setTotalPrice(BigDecimal.valueOf(price));
        booking.setCurrency("USD");
        booking.setPaymentStatus("PAID");
        booking.setPaymentMethod("VENUE");
        booking.setSource("MARKETPLACE");
        booking.setItems(new ArrayList<>());

        BookingItem item = new BookingItem();
        item.setBooking(booking);
        item.setService(service);
        item.setStaff(staff);
        item.setStartTime(start);
        item.setEndTime(start.plusMinutes(durationMinutes));
        item.setUnitPrice(BigDecimal.valueOf(price));
        item.setPriceMultiplier(BigDecimal.ONE);
        item.setLineTotal(BigDecimal.valueOf(price));
        item.setSortOrder(0);
        booking.getItems().add(item);

        Booking saved = bookingRepository.save(booking);
        saved.setPublicCode("HS" + String.format("%08d", saved.getId()));
        bookingRepository.save(saved);
    }
}
