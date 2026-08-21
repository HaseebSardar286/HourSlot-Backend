package com.hourslot.jdbc;

import com.hourslot.model.AuditEvent;
import com.hourslot.model.AuthRefreshToken;
import com.hourslot.model.Booking;
import com.hourslot.model.BookingItem;
import com.hourslot.model.BookingStatus;
import com.hourslot.model.BookingStatusHistory;
import com.hourslot.model.Branch;
import com.hourslot.model.BranchBreak;
import com.hourslot.model.BranchHoliday;
import com.hourslot.model.BranchWorkingHour;
import com.hourslot.model.Business;
import com.hourslot.model.BusinessMedia;
import com.hourslot.model.BusinessStatus;
import com.hourslot.model.BusinessVerificationDocument;
import com.hourslot.model.Category;
import com.hourslot.model.CustomerPackage;
import com.hourslot.model.CustomerProfile;
import com.hourslot.model.Favorite;
import com.hourslot.model.MediaAsset;
import com.hourslot.model.MemberRole;
import com.hourslot.model.Notification;
import com.hourslot.model.Organization;
import com.hourslot.model.OrganizationMember;
import com.hourslot.model.OrganizationSubscription;
import com.hourslot.model.PasswordResetToken;
import com.hourslot.model.Payment;
import com.hourslot.model.Permission;
import com.hourslot.model.PlanEntitlement;
import com.hourslot.model.Review;
import com.hourslot.model.Role;
import com.hourslot.model.Service;
import com.hourslot.model.ServicePackage;
import com.hourslot.model.Staff;
import com.hourslot.model.StaffBreak;
import com.hourslot.model.StaffInvite;
import com.hourslot.model.StaffService;
import com.hourslot.model.StaffTimeOff;
import com.hourslot.model.StaffWorkingHour;
import com.hourslot.model.SubscriptionPlan;
import com.hourslot.model.SystemSetting;
import com.hourslot.model.TimeOfDayPricing;
import com.hourslot.model.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class RowMappers {

    private final JdbcSupport jdbc;

    public final RowMapper<SubscriptionPlan> subscriptionPlan;
    public final RowMapper<Payment> payment;
    public final RowMapper<AuditEvent> auditEvent;
    public final RowMapper<CustomerProfile> customerProfile;
    public final RowMapper<OrganizationSubscription> organizationSubscription;
    public final RowMapper<Business> business;
    public final RowMapper<Service> service;

    public RowMappers(JdbcSupport jdbc) {
        this.jdbc = jdbc;
        this.subscriptionPlan = this::mapSubscriptionPlan;
        this.payment = this::mapPayment;
        this.auditEvent = this::mapAuditEvent;
        this.customerProfile = this::mapCustomerProfile;
        this.organizationSubscription = this::mapOrganizationSubscription;
        this.business = (rs, i) -> mapBusiness(rs);
        this.service = (rs, i) -> mapService(rs);
    }

    public static User refUser(Long id) {
        if (id == null) {
            return null;
        }
        User user = new User();
        user.setId(id);
        return user;
    }

    public static Organization refOrg(Long id) {
        if (id == null) {
            return null;
        }
        Organization organization = new Organization();
        organization.setId(id);
        return organization;
    }

    public static Business refBusiness(Long id) {
        if (id == null) {
            return null;
        }
        Business business = new Business();
        business.setId(id);
        return business;
    }

    public static Branch refBranch(Long id) {
        if (id == null) {
            return null;
        }
        Branch branch = new Branch();
        branch.setId(id);
        return branch;
    }

    public static Category refCategory(Long id) {
        if (id == null) {
            return null;
        }
        Category category = new Category();
        category.setId(id);
        return category;
    }

    public static Staff refStaff(Long id) {
        if (id == null) {
            return null;
        }
        Staff staff = new Staff();
        staff.setId(id);
        return staff;
    }

    public static Service refService(Long id) {
        if (id == null) {
            return null;
        }
        Service service = new Service();
        service.setId(id);
        return service;
    }

    public User mapUser(ResultSet rs, String p) throws SQLException {
        User user = new User();
        user.setId(JdbcSupport.getLong(rs, p + "id"));
        user.setEmail(rs.getString(p + "email"));
        user.setPasswordHash(rs.getString(p + "password_hash"));
        user.setPhoneNumber(rs.getString(p + "phone_number"));
        user.setStatus(rs.getString(p + "status"));
        user.setEmailVerifiedAt(JdbcSupport.localDateTime(rs, p + "email_verified_at"));
        user.setLocale(rs.getString(p + "locale"));
        user.setTimezone(rs.getString(p + "timezone"));
        user.setLastLoginAt(JdbcSupport.localDateTime(rs, p + "last_login_at"));
        user.setFirstName(rs.getString(p + "first_name"));
        user.setLastName(rs.getString(p + "last_name"));
        user.setCreatedAt(JdbcSupport.localDateTime(rs, p + "created_at"));
        user.setUpdatedAt(JdbcSupport.localDateTime(rs, p + "updated_at"));
        user.setDeletedAt(JdbcSupport.localDateTime(rs, p + "deleted_at"));
        return user;
    }

    public final RowMapper<User> user = (rs, i) -> mapUser(rs, "");

    public Organization mapOrganization(ResultSet rs, String p) throws SQLException {
        Organization organization = new Organization();
        organization.setId(JdbcSupport.getLong(rs, p + "id"));
        organization.setName(rs.getString(p + "name"));
        organization.setSlug(rs.getString(p + "slug"));
        organization.setBillingEmail(rs.getString(p + "billing_email"));
        organization.setStatus(rs.getString(p + "status"));
        organization.setStripeCustomerId(rs.getString(p + "stripe_customer_id"));
        organization.setStripeConnectAccountId(rs.getString(p + "stripe_connect_account_id"));
        organization.setDefaultCurrency(rs.getString(p + "default_currency"));
        organization.setCountryCode(rs.getString(p + "country_code"));
        organization.setTimezone(rs.getString(p + "timezone"));
        organization.setCreatedAt(JdbcSupport.localDateTime(rs, p + "created_at"));
        organization.setUpdatedAt(JdbcSupport.localDateTime(rs, p + "updated_at"));
        organization.setDeletedAt(JdbcSupport.localDateTime(rs, p + "deleted_at"));
        return organization;
    }

    public final RowMapper<Organization> organization = (rs, i) -> mapOrganization(rs, "");

    public Category mapCategory(ResultSet rs, String p) throws SQLException {
        Category category = new Category();
        category.setId(JdbcSupport.getLong(rs, p + "id"));
        category.setName(rs.getString(p + "name"));
        category.setSlug(rs.getString(p + "slug"));
        category.setIcon(rs.getString(p + "icon"));
        category.setImageUrl(rs.getString(p + "image_url"));
        category.setSearchTags(rs.getString(p + "search_tags"));
        category.setActive(rs.getBoolean(p + "is_active"));
        Integer sort = JdbcSupport.getInt(rs, p + "sort_order");
        category.setSortOrder(sort == null ? 0 : sort);
        category.setParent(refCategory(JdbcSupport.getLong(rs, p + "parent_id")));
        category.setCreatedAt(JdbcSupport.localDateTime(rs, p + "created_at"));
        return category;
    }

    public final RowMapper<Category> category = (rs, i) -> mapCategory(rs, "");

    public Business mapBusiness(ResultSet rs) throws SQLException {
        Business business = new Business();
        business.setId(JdbcSupport.getLong(rs, "id"));
        business.setOrganization(refOrg(JdbcSupport.getLong(rs, "organization_id")));
        business.setName(rs.getString("name"));
        business.setSlug(rs.getString("slug"));
        business.setDescription(rs.getString("description"));
        String status = rs.getString("status");
        business.setStatus(status == null ? null : BusinessStatus.valueOf(status));
        business.setVerified(rs.getBoolean("is_verified"));
        business.setRejectionReason(rs.getString("rejection_reason"));
        business.setRegistrationNumber(rs.getString("registration_number"));
        business.setPrimaryCategory(refCategory(JdbcSupport.getLong(rs, "primary_category_id")));
        BigDecimal avg = JdbcSupport.getDecimal(rs, "rating_avg");
        business.setRatingAvg(avg == null ? BigDecimal.ZERO : avg);
        Integer count = JdbcSupport.getInt(rs, "rating_count");
        business.setRatingCount(count == null ? 0 : count);
        business.setTimezone(rs.getString("timezone"));
        business.setLocale(rs.getString("locale"));
        business.setSettings(jdbc.readJsonb(rs, "settings"));
        try {
            business.setLogoUrl(rs.getString("logo_url"));
        } catch (SQLException ignored) {
            // optional alias
        }
        try {
            business.setGalleryUrls(rs.getString("gallery_urls"));
        } catch (SQLException ignored) {
            // optional alias
        }
        business.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        business.setUpdatedAt(JdbcSupport.localDateTime(rs, "updated_at"));
        business.setDeletedAt(JdbcSupport.localDateTime(rs, "deleted_at"));
        return business;
    }

    public static final String BUSINESS_MEDIA_FORMULAS = """
            (SELECT ma.url FROM business_media bm JOIN media_assets ma ON ma.id = bm.media_asset_id
              WHERE bm.business_id = b.id AND bm.role = 'logo' AND ma.deleted_at IS NULL LIMIT 1) AS logo_url,
            (SELECT string_agg(ma.url, ',' ORDER BY ma.sort_order) FROM business_media bm
              JOIN media_assets ma ON ma.id = bm.media_asset_id
              WHERE bm.business_id = b.id AND bm.role = 'gallery' AND ma.deleted_at IS NULL) AS gallery_urls
            """;

    public Branch mapBranch(ResultSet rs) throws SQLException {
        Branch branch = new Branch();
        branch.setId(JdbcSupport.getLong(rs, "id"));
        branch.setBusiness(refBusiness(JdbcSupport.getLong(rs, "business_id")));
        branch.setName(rs.getString("name"));
        branch.setAddress(rs.getString("address"));
        branch.setLatitude(JdbcSupport.getDouble(rs, "latitude"));
        branch.setLongitude(JdbcSupport.getDouble(rs, "longitude"));
        branch.setPhoneNumber(rs.getString("phone_number"));
        branch.setTimezone(rs.getString("timezone"));
        branch.setActive(rs.getBoolean("is_active"));
        Integer sort = JdbcSupport.getInt(rs, "sort_order");
        branch.setSortOrder(sort == null ? 0 : sort);
        branch.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        branch.setUpdatedAt(JdbcSupport.localDateTime(rs, "updated_at"));
        branch.setDeletedAt(JdbcSupport.localDateTime(rs, "deleted_at"));
        return branch;
    }

    public final RowMapper<Branch> branch = (rs, i) -> mapBranch(rs);

    public Staff mapStaff(ResultSet rs) throws SQLException {
        Staff staff = new Staff();
        staff.setId(JdbcSupport.getLong(rs, "id"));
        staff.setBranch(refBranch(JdbcSupport.getLong(rs, "branch_id")));
        staff.setUser(refUser(JdbcSupport.getLong(rs, "user_id")));
        staff.setDisplayName(rs.getString("display_name"));
        staff.setDesignation(rs.getString("designation"));
        staff.setSpecialty(rs.getString("specialty"));
        staff.setBio(rs.getString("bio"));
        BigDecimal avg = JdbcSupport.getDecimal(rs, "rating_avg");
        staff.setRatingAvg(avg == null ? BigDecimal.ZERO : avg);
        staff.setActive(rs.getBoolean("is_active"));
        Integer sort = JdbcSupport.getInt(rs, "sort_order");
        staff.setSortOrder(sort == null ? 0 : sort);
        staff.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        staff.setUpdatedAt(JdbcSupport.localDateTime(rs, "updated_at"));
        staff.setDeletedAt(JdbcSupport.localDateTime(rs, "deleted_at"));
        return staff;
    }

    public final RowMapper<Staff> staff = (rs, i) -> mapStaff(rs);

    public Service mapService(ResultSet rs) throws SQLException {
        Service service = new Service();
        service.setId(JdbcSupport.getLong(rs, "id"));
        service.setBusiness(refBusiness(JdbcSupport.getLong(rs, "business_id")));
        service.setName(rs.getString("name"));
        service.setDescription(rs.getString("description"));
        service.setBasePrice(JdbcSupport.getDecimal(rs, "base_price"));
        service.setCurrency(rs.getString("currency"));
        Integer duration = JdbcSupport.getInt(rs, "duration_minutes");
        service.setDurationMinutes(duration == null ? 0 : duration);
        Integer buffer = JdbcSupport.getInt(rs, "buffer_minutes");
        service.setBufferMinutes(buffer == null ? 0 : buffer);
        Integer maxConcurrent = JdbcSupport.getInt(rs, "max_concurrent");
        service.setMaxConcurrent(maxConcurrent == null ? 1 : maxConcurrent);
        service.setActive(rs.getBoolean("is_active"));
        Integer capacity = JdbcSupport.getInt(rs, "capacity");
        service.setCapacity(capacity == null ? 1 : capacity);
        service.setGroupService(rs.getBoolean("is_group_service"));
        Integer sort = JdbcSupport.getInt(rs, "sort_order");
        service.setSortOrder(sort == null ? 0 : sort);
        service.setMetadata(jdbc.readJsonb(rs, "metadata"));
        service.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        service.setUpdatedAt(JdbcSupport.localDateTime(rs, "updated_at"));
        service.setDeletedAt(JdbcSupport.localDateTime(rs, "deleted_at"));
        return service;
    }

    public final RowMapper<Role> role = (rs, i) -> {
        Role role = new Role();
        role.setId(JdbcSupport.getLong(rs, "id"));
        role.setScope(rs.getString("scope"));
        role.setOrganization(refOrg(JdbcSupport.getLong(rs, "organization_id")));
        role.setBusiness(refBusiness(JdbcSupport.getLong(rs, "business_id")));
        role.setCode(rs.getString("code"));
        role.setName(rs.getString("name"));
        role.setSystem(rs.getBoolean("is_system"));
        role.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        return role;
    };

    public final RowMapper<Permission> permission = (rs, i) -> {
        Permission permission = new Permission();
        permission.setId(JdbcSupport.getLong(rs, "id"));
        permission.setCode(rs.getString("code"));
        permission.setModule(rs.getString("module"));
        permission.setDescription(rs.getString("description"));
        permission.setActive(rs.getBoolean("is_active"));
        return permission;
    };

    public final RowMapper<PlanEntitlement> planEntitlement = (rs, i) -> {
        PlanEntitlement row = new PlanEntitlement();
        row.setId(JdbcSupport.getLong(rs, "id"));
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(JdbcSupport.getLong(rs, "plan_id"));
        row.setPlan(plan);
        row.setEntitlementCode(rs.getString("entitlement_code"));
        row.setValueType(rs.getString("value_type"));
        row.setValue(rs.getString("value"));
        return row;
    };

    public SubscriptionPlan mapSubscriptionPlan(ResultSet rs, int i) throws SQLException {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(JdbcSupport.getLong(rs, "id"));
        plan.setCode(rs.getString("code"));
        plan.setName(rs.getString("name"));
        plan.setBillingInterval(rs.getString("billing_interval"));
        plan.setPrice(JdbcSupport.getDecimal(rs, "price"));
        plan.setCurrency(rs.getString("currency"));
        plan.setStripePriceId(rs.getString("stripe_price_id"));
        plan.setActive(rs.getBoolean("is_active"));
        Integer sort = JdbcSupport.getInt(rs, "sort_order");
        plan.setSortOrder(sort == null ? 0 : sort);
        plan.setFeatures(jdbc.readJsonb(rs, "features"));
        return plan;
    }

    public final RowMapper<Notification> notification = (rs, i) -> {
        Notification notification = new Notification();
        notification.setId(JdbcSupport.getLong(rs, "id"));
        notification.setUser(refUser(JdbcSupport.getLong(rs, "user_id")));
        notification.setChannel(rs.getString("channel"));
        notification.setTitle(rs.getString("title"));
        notification.setBody(rs.getString("body"));
        notification.setRead(rs.getBoolean("is_read"));
        notification.setSentAt(JdbcSupport.localDateTime(rs, "sent_at"));
        notification.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        return notification;
    };

    public final RowMapper<MediaAsset> mediaAsset = (rs, i) -> {
        MediaAsset asset = new MediaAsset();
        asset.setId(JdbcSupport.getLong(rs, "id"));
        asset.setOwnerType(rs.getString("owner_type"));
        asset.setOwnerId(JdbcSupport.getLong(rs, "owner_id"));
        asset.setStorageKey(rs.getString("storage_key"));
        asset.setUrl(rs.getString("url"));
        asset.setMimeType(rs.getString("mime_type"));
        asset.setBytes(JdbcSupport.getLong(rs, "bytes"));
        Integer sort = JdbcSupport.getInt(rs, "sort_order");
        asset.setSortOrder(sort == null ? 0 : sort);
        asset.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        asset.setDeletedAt(JdbcSupport.localDateTime(rs, "deleted_at"));
        return asset;
    };

    public final RowMapper<Favorite> favorite = (rs, i) -> {
        Favorite favorite = new Favorite();
        favorite.setId(JdbcSupport.getLong(rs, "id"));
        favorite.setCustomerUser(refUser(JdbcSupport.getLong(rs, "customer_user_id")));
        favorite.setBusiness(refBusiness(JdbcSupport.getLong(rs, "business_id")));
        favorite.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        return favorite;
    };

    public final RowMapper<Review> review = (rs, i) -> {
        Review review = new Review();
        review.setId(JdbcSupport.getLong(rs, "id"));
        review.setCustomerUser(refUser(JdbcSupport.getLong(rs, "customer_user_id")));
        review.setBusiness(refBusiness(JdbcSupport.getLong(rs, "business_id")));
        Booking booking = new Booking();
        booking.setId(JdbcSupport.getLong(rs, "booking_id"));
        review.setBooking(booking);
        review.setRating(rs.getInt("rating"));
        review.setComment(rs.getString("comment"));
        review.setOwnerReply(rs.getString("owner_reply"));
        review.setOwnerRepliedAt(JdbcSupport.localDateTime(rs, "owner_replied_at"));
        review.setVisible(rs.getBoolean("is_visible"));
        review.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        review.setUpdatedAt(JdbcSupport.localDateTime(rs, "updated_at"));
        review.setDeletedAt(JdbcSupport.localDateTime(rs, "deleted_at"));
        return review;
    };

    public Payment mapPayment(ResultSet rs, int i) throws SQLException {
        Payment payment = new Payment();
        payment.setId(JdbcSupport.getLong(rs, "id"));
        payment.setOrganization(refOrg(JdbcSupport.getLong(rs, "organization_id")));
        payment.setBusiness(refBusiness(JdbcSupport.getLong(rs, "business_id")));
        payment.setUser(refUser(JdbcSupport.getLong(rs, "user_id")));
        payment.setPurpose(rs.getString("purpose"));
        payment.setReferenceType(rs.getString("reference_type"));
        payment.setReferenceId(JdbcSupport.getLong(rs, "reference_id"));
        payment.setProvider(rs.getString("provider"));
        payment.setProviderPaymentId(rs.getString("provider_payment_id"));
        payment.setAmount(JdbcSupport.getDecimal(rs, "amount"));
        payment.setCurrency(rs.getString("currency"));
        payment.setStatus(rs.getString("status"));
        payment.setRawPayload(jdbc.readJsonb(rs, "raw_payload"));
        payment.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        payment.setUpdatedAt(JdbcSupport.localDateTime(rs, "updated_at"));
        payment.setVersion(JdbcSupport.getInt(rs, "version"));
        return payment;
    }

    public final RowMapper<SystemSetting> systemSetting = (rs, i) -> {
        SystemSetting setting = new SystemSetting();
        setting.setKey(rs.getString("key"));
        setting.setValue(rs.getString("value"));
        setting.setUpdatedAt(JdbcSupport.localDateTime(rs, "updated_at"));
        setting.setUpdatedBy(refUser(JdbcSupport.getLong(rs, "updated_by")));
        return setting;
    };

    public final RowMapper<PasswordResetToken> passwordResetToken = (rs, i) -> {
        PasswordResetToken token = new PasswordResetToken();
        token.setId(JdbcSupport.getLong(rs, "id"));
        token.setTokenHash(rs.getString("token_hash"));
        token.setUser(refUser(JdbcSupport.getLong(rs, "user_id")));
        token.setExpiresAt(JdbcSupport.localDateTime(rs, "expires_at"));
        token.setUsed(rs.getBoolean("used"));
        token.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        return token;
    };

    public final RowMapper<AuthRefreshToken> authRefreshToken = (rs, i) -> {
        AuthRefreshToken token = new AuthRefreshToken();
        token.setId(JdbcSupport.getLong(rs, "id"));
        token.setUser(refUser(JdbcSupport.getLong(rs, "user_id")));
        token.setTokenHash(rs.getString("token_hash"));
        token.setExpiresAt(JdbcSupport.localDateTime(rs, "expires_at"));
        token.setRevokedAt(JdbcSupport.localDateTime(rs, "revoked_at"));
        token.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        token.setUserAgent(rs.getString("user_agent"));
        token.setIpAddress(rs.getString("ip_address"));
        return token;
    };

    public AuditEvent mapAuditEvent(ResultSet rs, int i) throws SQLException {
        AuditEvent event = new AuditEvent();
        event.setId(JdbcSupport.getLong(rs, "id"));
        event.setActor(refUser(JdbcSupport.getLong(rs, "actor_user_id")));
        event.setOrganization(refOrg(JdbcSupport.getLong(rs, "organization_id")));
        event.setBusiness(refBusiness(JdbcSupport.getLong(rs, "business_id")));
        event.setAction(rs.getString("action"));
        event.setEntityType(rs.getString("entity_type"));
        event.setEntityId(JdbcSupport.getLong(rs, "entity_id"));
        event.setBeforeState(jdbc.readJsonb(rs, "before_state"));
        event.setAfterState(jdbc.readJsonb(rs, "after_state"));
        event.setIpAddress(rs.getString("ip_address"));
        event.setUserAgent(rs.getString("user_agent"));
        event.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        return event;
    }

    public CustomerProfile mapCustomerProfile(ResultSet rs, int i) throws SQLException {
        CustomerProfile profile = new CustomerProfile();
        profile.setUserId(JdbcSupport.getLong(rs, "user_id"));
        profile.setUser(refUser(profile.getUserId()));
        profile.setDateOfBirth(JdbcSupport.localDate(rs, "date_of_birth"));
        profile.setGender(rs.getString("gender"));
        profile.setAddressLine1(rs.getString("address_line1"));
        profile.setAddressLine2(rs.getString("address_line2"));
        profile.setCity(rs.getString("city"));
        profile.setRegion(rs.getString("region"));
        profile.setPostalCode(rs.getString("postal_code"));
        profile.setCountryCode(rs.getString("country_code"));
        profile.setMetadata(jdbc.readJsonb(rs, "metadata"));
        profile.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        profile.setUpdatedAt(JdbcSupport.localDateTime(rs, "updated_at"));
        profile.setDeletedAt(JdbcSupport.localDateTime(rs, "deleted_at"));
        return profile;
    }

    public Booking mapBooking(ResultSet rs) throws SQLException {
        Booking booking = new Booking();
        booking.setId(JdbcSupport.getLong(rs, "id"));
        booking.setPublicCode(rs.getString("public_code"));
        booking.setCustomerUser(refUser(JdbcSupport.getLong(rs, "customer_user_id")));
        booking.setOrganization(refOrg(JdbcSupport.getLong(rs, "organization_id")));
        booking.setBusiness(refBusiness(JdbcSupport.getLong(rs, "business_id")));
        booking.setBranch(refBranch(JdbcSupport.getLong(rs, "branch_id")));
        booking.setBookingTime(JdbcSupport.localDateTime(rs, "booking_time"));
        booking.setEndTime(JdbcSupport.localDateTime(rs, "end_time"));
        String status = rs.getString("status");
        booking.setStatus(status == null ? null : BookingStatus.valueOf(status));
        booking.setTotalPrice(JdbcSupport.getDecimal(rs, "total_price"));
        booking.setCurrency(rs.getString("currency"));
        booking.setPaymentStatus(rs.getString("payment_status"));
        booking.setPaymentMethod(rs.getString("payment_method"));
        booking.setClientNotes(rs.getString("client_notes"));
        booking.setInternalNotes(rs.getString("internal_notes"));
        booking.setSource(rs.getString("source"));
        Long pkgId = JdbcSupport.getLong(rs, "customer_package_id");
        if (pkgId != null) {
            CustomerPackage pkg = new CustomerPackage();
            pkg.setId(pkgId);
            booking.setCustomerPackage(pkg);
        }
        booking.setVersion(JdbcSupport.getInt(rs, "version"));
        booking.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        booking.setUpdatedAt(JdbcSupport.localDateTime(rs, "updated_at"));
        booking.setDeletedAt(JdbcSupport.localDateTime(rs, "deleted_at"));
        return booking;
    }

    public final RowMapper<Booking> booking = (rs, i) -> mapBooking(rs);

    public final RowMapper<BookingItem> bookingItem = (rs, i) -> {
        BookingItem item = new BookingItem();
        item.setId(JdbcSupport.getLong(rs, "id"));
        Booking booking = new Booking();
        booking.setId(JdbcSupport.getLong(rs, "booking_id"));
        item.setBooking(booking);
        item.setService(refService(JdbcSupport.getLong(rs, "service_id")));
        item.setStaff(refStaff(JdbcSupport.getLong(rs, "staff_id")));
        item.setStartTime(JdbcSupport.localDateTime(rs, "start_time"));
        item.setEndTime(JdbcSupport.localDateTime(rs, "end_time"));
        item.setUnitPrice(JdbcSupport.getDecimal(rs, "unit_price"));
        item.setPriceMultiplier(JdbcSupport.getDecimal(rs, "price_multiplier"));
        item.setLineTotal(JdbcSupport.getDecimal(rs, "line_total"));
        item.setSortOrder(JdbcSupport.getInt(rs, "sort_order"));
        return item;
    };

    public final RowMapper<BookingStatusHistory> bookingStatusHistory = (rs, i) -> {
        BookingStatusHistory history = new BookingStatusHistory();
        history.setId(JdbcSupport.getLong(rs, "id"));
        Booking booking = new Booking();
        booking.setId(JdbcSupport.getLong(rs, "booking_id"));
        history.setBooking(booking);
        history.setFromStatus(rs.getString("from_status"));
        history.setToStatus(rs.getString("to_status"));
        history.setChangedBy(refUser(JdbcSupport.getLong(rs, "changed_by_user_id")));
        history.setReason(rs.getString("reason"));
        history.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        return history;
    };

    public final RowMapper<BranchWorkingHour> branchWorkingHour = (rs, i) -> {
        BranchWorkingHour hour = new BranchWorkingHour();
        hour.setId(JdbcSupport.getLong(rs, "id"));
        hour.setBranch(refBranch(JdbcSupport.getLong(rs, "branch_id")));
        hour.setDayOfWeek(rs.getInt("day_of_week"));
        hour.setStartTime(JdbcSupport.localTime(rs, "start_time"));
        hour.setEndTime(JdbcSupport.localTime(rs, "end_time"));
        hour.setClosed(rs.getBoolean("closed"));
        return hour;
    };

    public final RowMapper<BranchBreak> branchBreak = (rs, i) -> {
        BranchBreak br = new BranchBreak();
        br.setId(JdbcSupport.getLong(rs, "id"));
        BranchWorkingHour hour = new BranchWorkingHour();
        hour.setId(JdbcSupport.getLong(rs, "working_hour_id"));
        br.setWorkingHour(hour);
        br.setStartTime(JdbcSupport.localTime(rs, "start_time"));
        br.setEndTime(JdbcSupport.localTime(rs, "end_time"));
        return br;
    };

    public final RowMapper<StaffWorkingHour> staffWorkingHour = (rs, i) -> {
        StaffWorkingHour hour = new StaffWorkingHour();
        hour.setId(JdbcSupport.getLong(rs, "id"));
        hour.setStaff(refStaff(JdbcSupport.getLong(rs, "staff_id")));
        hour.setDayOfWeek(rs.getInt("day_of_week"));
        hour.setStartTime(JdbcSupport.localTime(rs, "start_time"));
        hour.setEndTime(JdbcSupport.localTime(rs, "end_time"));
        hour.setClosed(rs.getBoolean("closed"));
        return hour;
    };

    public final RowMapper<StaffBreak> staffBreak = (rs, i) -> {
        StaffBreak br = new StaffBreak();
        br.setId(JdbcSupport.getLong(rs, "id"));
        StaffWorkingHour hour = new StaffWorkingHour();
        hour.setId(JdbcSupport.getLong(rs, "working_hour_id"));
        br.setWorkingHour(hour);
        br.setStartTime(JdbcSupport.localTime(rs, "start_time"));
        br.setEndTime(JdbcSupport.localTime(rs, "end_time"));
        return br;
    };

    public final RowMapper<BranchHoliday> branchHoliday = (rs, i) -> {
        BranchHoliday holiday = new BranchHoliday();
        holiday.setId(JdbcSupport.getLong(rs, "id"));
        holiday.setBranch(refBranch(JdbcSupport.getLong(rs, "branch_id")));
        holiday.setHolidayDate(JdbcSupport.localDate(rs, "holiday_date"));
        holiday.setDescription(rs.getString("description"));
        return holiday;
    };

    public final RowMapper<StaffTimeOff> staffTimeOff = (rs, i) -> {
        StaffTimeOff timeOff = new StaffTimeOff();
        timeOff.setId(JdbcSupport.getLong(rs, "id"));
        timeOff.setStaff(refStaff(JdbcSupport.getLong(rs, "staff_id")));
        timeOff.setStartAt(JdbcSupport.localDateTime(rs, "start_at"));
        timeOff.setEndAt(JdbcSupport.localDateTime(rs, "end_at"));
        timeOff.setReason(rs.getString("reason"));
        timeOff.setStatus(rs.getString("status"));
        return timeOff;
    };

    public final RowMapper<StaffService> staffService = (rs, i) -> {
        StaffService row = new StaffService();
        row.setId(JdbcSupport.getLong(rs, "id"));
        row.setStaff(refStaff(JdbcSupport.getLong(rs, "staff_id")));
        row.setService(refService(JdbcSupport.getLong(rs, "service_id")));
        row.setPriceOverride(JdbcSupport.getDouble(rs, "price_override"));
        return row;
    };

    public final RowMapper<TimeOfDayPricing> timeOfDayPricing = (rs, i) -> {
        TimeOfDayPricing pricing = new TimeOfDayPricing();
        pricing.setId(JdbcSupport.getLong(rs, "id"));
        pricing.setService(refService(JdbcSupport.getLong(rs, "service_id")));
        pricing.setDayOfWeek(rs.getInt("day_of_week"));
        pricing.setStartTime(JdbcSupport.localTime(rs, "start_time"));
        pricing.setEndTime(JdbcSupport.localTime(rs, "end_time"));
        pricing.setPriceMultiplier(rs.getDouble("price_multiplier"));
        pricing.setLabel(rs.getString("label"));
        pricing.setActive(rs.getBoolean("is_active"));
        return pricing;
    };

    public final RowMapper<ServicePackage> servicePackage = (rs, i) -> {
        ServicePackage pkg = new ServicePackage();
        pkg.setId(JdbcSupport.getLong(rs, "id"));
        pkg.setBusiness(refBusiness(JdbcSupport.getLong(rs, "business_id")));
        pkg.setName(rs.getString("name"));
        pkg.setDescription(rs.getString("description"));
        pkg.setPrice(rs.getDouble("price"));
        pkg.setCurrency(rs.getString("currency"));
        pkg.setSessionsCount(rs.getInt("sessions_count"));
        Integer expiry = JdbcSupport.getInt(rs, "expiry_days");
        pkg.setExpiryDays(expiry == null ? 0 : expiry);
        pkg.setActive(rs.getBoolean("is_active"));
        pkg.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        pkg.setUpdatedAt(JdbcSupport.localDateTime(rs, "updated_at"));
        pkg.setDeletedAt(JdbcSupport.localDateTime(rs, "deleted_at"));
        return pkg;
    };

    public final RowMapper<CustomerPackage> customerPackage = (rs, i) -> {
        CustomerPackage pkg = new CustomerPackage();
        pkg.setId(JdbcSupport.getLong(rs, "id"));
        pkg.setCustomerUser(refUser(JdbcSupport.getLong(rs, "customer_user_id")));
        ServicePackage sp = new ServicePackage();
        sp.setId(JdbcSupport.getLong(rs, "package_id"));
        pkg.setServicePackage(sp);
        pkg.setBusiness(refBusiness(JdbcSupport.getLong(rs, "business_id")));
        pkg.setSessionsRemaining(rs.getInt("sessions_remaining"));
        pkg.setExpiresAt(JdbcSupport.localDateTime(rs, "expires_at"));
        pkg.setStatus(rs.getString("status"));
        pkg.setPurchasePaymentId(JdbcSupport.getLong(rs, "purchase_payment_id"));
        pkg.setVersion(JdbcSupport.getInt(rs, "version"));
        pkg.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        pkg.setUpdatedAt(JdbcSupport.localDateTime(rs, "updated_at"));
        return pkg;
    };

    public final RowMapper<OrganizationMember> organizationMember = (rs, i) -> {
        OrganizationMember member = new OrganizationMember();
        member.setId(JdbcSupport.getLong(rs, "id"));
        member.setOrganization(refOrg(JdbcSupport.getLong(rs, "organization_id")));
        member.setUser(refUser(JdbcSupport.getLong(rs, "user_id")));
        member.setStatus(rs.getString("status"));
        member.setInvitedBy(refUser(JdbcSupport.getLong(rs, "invited_by")));
        member.setJoinedAt(JdbcSupport.localDateTime(rs, "joined_at"));
        member.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        member.setUpdatedAt(JdbcSupport.localDateTime(rs, "updated_at"));
        member.setDeletedAt(JdbcSupport.localDateTime(rs, "deleted_at"));
        return member;
    };

    public OrganizationSubscription mapOrganizationSubscription(ResultSet rs, int i) throws SQLException {
        OrganizationSubscription sub = new OrganizationSubscription();
        sub.setId(JdbcSupport.getLong(rs, "id"));
        sub.setOrganization(refOrg(JdbcSupport.getLong(rs, "organization_id")));
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(JdbcSupport.getLong(rs, "plan_id"));
        sub.setPlan(plan);
        sub.setStatus(rs.getString("status"));
        sub.setStripeSubscriptionId(rs.getString("stripe_subscription_id"));
        sub.setCurrentPeriodStart(JdbcSupport.localDateTime(rs, "current_period_start"));
        sub.setCurrentPeriodEnd(JdbcSupport.localDateTime(rs, "current_period_end"));
        sub.setCancelAtPeriodEnd(rs.getBoolean("cancel_at_period_end"));
        sub.setEntitlementOverrides(jdbc.readJsonb(rs, "entitlement_overrides"));
        sub.setVersion(JdbcSupport.getInt(rs, "version"));
        sub.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        sub.setUpdatedAt(JdbcSupport.localDateTime(rs, "updated_at"));
        return sub;
    }

    public final RowMapper<MemberRole> memberRole = (rs, i) -> {
        MemberRole row = new MemberRole();
        row.setId(JdbcSupport.getLong(rs, "id"));
        row.setUser(refUser(JdbcSupport.getLong(rs, "user_id")));
        Role role = new Role();
        role.setId(JdbcSupport.getLong(rs, "role_id"));
        row.setRole(role);
        row.setOrganization(refOrg(JdbcSupport.getLong(rs, "organization_id")));
        row.setBusiness(refBusiness(JdbcSupport.getLong(rs, "business_id")));
        row.setBranch(refBranch(JdbcSupport.getLong(rs, "branch_id")));
        row.setStaff(refStaff(JdbcSupport.getLong(rs, "staff_id")));
        row.setGrantedAt(JdbcSupport.localDateTime(rs, "granted_at"));
        row.setGrantedBy(refUser(JdbcSupport.getLong(rs, "granted_by")));
        row.setExpiresAt(JdbcSupport.localDateTime(rs, "expires_at"));
        row.setDeletedAt(JdbcSupport.localDateTime(rs, "deleted_at"));
        return row;
    };

    public final RowMapper<BusinessMedia> businessMedia = (rs, i) -> {
        BusinessMedia media = new BusinessMedia();
        media.setBusinessId(JdbcSupport.getLong(rs, "business_id"));
        media.setMediaAssetId(JdbcSupport.getLong(rs, "media_asset_id"));
        media.setBusiness(refBusiness(media.getBusinessId()));
        media.setRole(rs.getString("role"));
        return media;
    };

    public final RowMapper<BusinessVerificationDocument> verificationDocument = (rs, i) -> {
        BusinessVerificationDocument doc = new BusinessVerificationDocument();
        doc.setId(JdbcSupport.getLong(rs, "id"));
        doc.setBusiness(refBusiness(JdbcSupport.getLong(rs, "business_id")));
        doc.setDocumentType(rs.getString("document_type"));
        MediaAsset asset = new MediaAsset();
        asset.setId(JdbcSupport.getLong(rs, "media_asset_id"));
        doc.setMediaAsset(asset);
        doc.setOriginalFilename(rs.getString("original_filename"));
        doc.setStatus(rs.getString("status"));
        doc.setReviewNotes(rs.getString("review_notes"));
        doc.setReviewedBy(refUser(JdbcSupport.getLong(rs, "reviewed_by_user_id")));
        doc.setReviewedAt(JdbcSupport.localDateTime(rs, "reviewed_at"));
        doc.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        doc.setUpdatedAt(JdbcSupport.localDateTime(rs, "updated_at"));
        doc.setDeletedAt(JdbcSupport.localDateTime(rs, "deleted_at"));
        return doc;
    };

    public final RowMapper<StaffInvite> staffInvite = (rs, i) -> {
        StaffInvite invite = new StaffInvite();
        invite.setId(JdbcSupport.getLong(rs, "id"));
        invite.setOrganization(refOrg(JdbcSupport.getLong(rs, "organization_id")));
        invite.setBusiness(refBusiness(JdbcSupport.getLong(rs, "business_id")));
        invite.setBranch(refBranch(JdbcSupport.getLong(rs, "branch_id")));
        invite.setEmail(rs.getString("email"));
        invite.setDisplayName(rs.getString("display_name"));
        invite.setDesignation(rs.getString("designation"));
        invite.setTokenHash(rs.getString("token_hash"));
        invite.setStatus(rs.getString("status"));
        invite.setInvitedBy(refUser(JdbcSupport.getLong(rs, "invited_by_user_id")));
        invite.setExpiresAt(JdbcSupport.localDateTime(rs, "expires_at"));
        invite.setAcceptedAt(JdbcSupport.localDateTime(rs, "accepted_at"));
        invite.setCreatedAt(JdbcSupport.localDateTime(rs, "created_at"));
        return invite;
    };
}
