package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Booking;
import com.hourslot.model.BookingItem;
import com.hourslot.model.BookingStatus;
import com.hourslot.model.Branch;
import com.hourslot.model.Business;
import com.hourslot.model.Staff;
import com.hourslot.model.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Repository
public class BookingRepository {

    private static final String COLUMNS = """
            id, public_code, customer_user_id, organization_id, business_id, branch_id,
            booking_time, end_time, status, total_price, currency, payment_status, payment_method,
            client_notes, internal_notes, source, customer_package_id, version, created_at, updated_at, deleted_at
            """;

    private static final String SELECT = "SELECT " + COLUMNS + " FROM bookings";

    private static final String SELECT_B = """
            SELECT DISTINCT b.id, b.public_code, b.customer_user_id, b.organization_id, b.business_id, b.branch_id,
                   b.booking_time, b.end_time, b.status, b.total_price, b.currency, b.payment_status, b.payment_method,
                   b.client_notes, b.internal_notes, b.source, b.customer_package_id, b.version, b.created_at, b.updated_at, b.deleted_at
            FROM bookings b
            """;

    private static final String USER_SELECT = """
            SELECT id, email, password_hash, phone_number, status, email_verified_at, locale, timezone,
                   last_login_at, first_name, last_name, created_at, updated_at, deleted_at
            FROM users WHERE id IN (:ids)
            """;

    private static final String BRANCH_SELECT = """
            SELECT id, business_id, name, address, latitude, longitude, phone_number, timezone,
                   is_active, sort_order, created_at, updated_at, deleted_at
            FROM branches WHERE id IN (:ids)
            """;

    private static final String BUSINESS_SELECT = """
            SELECT id, organization_id, name, slug, description, status, is_verified, rejection_reason,
                   registration_number, primary_category_id, rating_avg, rating_count, timezone, locale,
                   settings, created_at, updated_at, deleted_at
            FROM businesses WHERE id IN (:ids)
            """;

    private static final String SERVICE_SELECT = """
            SELECT id, business_id, name, description, base_price, currency, duration_minutes, buffer_minutes,
                   max_concurrent, is_active, capacity, is_group_service, sort_order, metadata,
                   created_at, updated_at, deleted_at
            FROM services WHERE id IN (:ids)
            """;

    private static final String STAFF_SELECT = """
            SELECT id, branch_id, user_id, display_name, designation, specialty, bio, rating_avg,
                   is_active, sort_order, created_at, updated_at, deleted_at
            FROM staff WHERE id IN (:ids)
            """;

    private static final String ITEM_SELECT = """
            SELECT id, booking_id, service_id, staff_id, start_time, end_time, unit_price, price_multiplier,
                   line_total, sort_order
            FROM booking_items WHERE booking_id IN (:ids) ORDER BY sort_order, id
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;
    private final CustomerPackageRepository customerPackageRepository;

    public BookingRepository(JdbcSupport jdbc, RowMappers rows, CustomerPackageRepository customerPackageRepository) {
        this.jdbc = jdbc;
        this.rows = rows;
        this.customerPackageRepository = customerPackageRepository;
    }

    public Optional<Booking> findById(Long id) {
        return findOne(SELECT + " WHERE id = :id AND deleted_at IS NULL", jdbc.params().addValue("id", id));
    }

    public List<Booking> findAll() {
        return findBookings(SELECT + " WHERE deleted_at IS NULL ORDER BY id", jdbc.params());
    }

    public long count() {
        return jdbc.count("SELECT COUNT(*) FROM bookings WHERE deleted_at IS NULL", jdbc.params());
    }

    public List<Booking> findByStaffAndBookingTimeBetweenAndStatusIn(
            Staff staff, LocalDateTime start, LocalDateTime end, List<BookingStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        List<Booking> bookings = findBookings(SELECT_B + """
                JOIN booking_items i ON i.booking_id = b.id
                WHERE i.staff_id = :staffId
                  AND b.booking_time BETWEEN :start AND :end
                  AND b.status IN (:statuses)
                  AND b.deleted_at IS NULL
                """, bindRange(staff.getId(), start, end, statuses, "staffId"));
        attachDetails(bookings);
        return bookings;
    }

    public List<Booking> findByBranchAndBookingTimeBetweenAndStatusIn(
            Branch branch, LocalDateTime start, LocalDateTime end, List<BookingStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        List<Booking> bookings = findBookings(SELECT + """
                WHERE branch_id = :branchId
                  AND booking_time BETWEEN :start AND :end
                  AND status IN (:statuses)
                  AND deleted_at IS NULL
                """, bindRange(branch.getId(), start, end, statuses, "branchId"));
        attachDetails(bookings);
        return bookings;
    }

    public List<Booking> findByBranch(Branch branch) {
        return findBookings(SELECT + " WHERE branch_id = :branchId AND deleted_at IS NULL",
                jdbc.params().addValue("branchId", branch.getId()));
    }

    public List<Booking> findByBranchOrderByBookingTimeDesc(Branch branch) {
        return findBookings(SELECT + " WHERE branch_id = :branchId AND deleted_at IS NULL ORDER BY booking_time DESC",
                jdbc.params().addValue("branchId", branch.getId()));
    }

    public List<Booking> findByCustomerUserOrderByBookingTimeDesc(User customerUser) {
        return findBookings(SELECT + " WHERE customer_user_id = :userId AND deleted_at IS NULL ORDER BY booking_time DESC",
                jdbc.params().addValue("userId", customerUser.getId()));
    }

    public List<Booking> findByBranchWithDetails(Branch branch) {
        List<Booking> bookings = findBookings(
                SELECT + " WHERE branch_id = :branchId AND deleted_at IS NULL ORDER BY booking_time DESC",
                jdbc.params().addValue("branchId", branch.getId()));
        attachDetails(bookings);
        return bookings;
    }

    public List<Booking> findByCustomerUserWithDetails(User customer) {
        List<Booking> bookings = findBookings(
                SELECT + " WHERE customer_user_id = :userId AND deleted_at IS NULL ORDER BY booking_time DESC",
                jdbc.params().addValue("userId", customer.getId()));
        attachDetails(bookings);
        return bookings;
    }

    public Optional<Booking> findByIdWithDetails(Long id) {
        Optional<Booking> found = findOne(SELECT + " WHERE id = :id AND deleted_at IS NULL",
                jdbc.params().addValue("id", id));
        found.ifPresent(booking -> {
            attachDetails(List.of(booking));
            if (booking.getCustomerPackage() != null && booking.getCustomerPackage().getId() != null) {
                customerPackageRepository.findById(booking.getCustomerPackage().getId())
                        .ifPresent(booking::setCustomerPackage);
            }
        });
        return found;
    }

    @Transactional
    public Booking save(Booking booking) {
        if (booking.getId() == null) {
            booking.onCreate();
            int version = booking.getVersion() == null ? 1 : booking.getVersion();
            booking.setVersion(version);
            Long id = jdbc.insert("""
                    INSERT INTO bookings (
                        public_code, customer_user_id, organization_id, business_id, branch_id,
                        booking_time, end_time, status, total_price, currency, payment_status, payment_method,
                        client_notes, internal_notes, source, customer_package_id, version, created_at, updated_at)
                    VALUES (
                        :publicCode, :customerUserId, :organizationId, :businessId, :branchId,
                        :bookingTime, :endTime, :status, :totalPrice, :currency, :paymentStatus, :paymentMethod,
                        :clientNotes, :internalNotes, :source, :customerPackageId, :version, :createdAt, :updatedAt)
                    """, bind(booking));
            booking.setId(id);
        } else {
            booking.onUpdate();
            jdbc.update("""
                    UPDATE bookings SET
                        public_code = :publicCode,
                        customer_user_id = :customerUserId,
                        organization_id = :organizationId,
                        business_id = :businessId,
                        branch_id = :branchId,
                        booking_time = :bookingTime,
                        end_time = :endTime,
                        status = :status,
                        total_price = :totalPrice,
                        currency = :currency,
                        payment_status = :paymentStatus,
                        payment_method = :paymentMethod,
                        client_notes = :clientNotes,
                        internal_notes = :internalNotes,
                        source = :source,
                        customer_package_id = :customerPackageId,
                        version = COALESCE(version, 1) + 1,
                        updated_at = :updatedAt
                    WHERE id = :id
                    """, bind(booking).addValue("id", booking.getId()));
            booking.setVersion((booking.getVersion() == null ? 1 : booking.getVersion()) + 1);
        }
        saveItems(booking);
        return booking;
    }

    private void saveItems(Booking booking) {
        List<BookingItem> items = booking.getItems();
        if (items == null) {
            return;
        }
        Set<Long> keepIds = new LinkedHashSet<>();
        for (BookingItem item : items) {
            item.setBooking(booking);
            if (item.getId() == null) {
                Long id = jdbc.insert("""
                        INSERT INTO booking_items (
                            booking_id, service_id, staff_id, start_time, end_time,
                            unit_price, price_multiplier, line_total, sort_order)
                        VALUES (
                            :bookingId, :serviceId, :staffId, :startTime, :endTime,
                            :unitPrice, :priceMultiplier, :lineTotal, :sortOrder)
                        """, bindItem(item));
                item.setId(id);
            } else {
                jdbc.update("""
                        UPDATE booking_items SET
                            booking_id = :bookingId,
                            service_id = :serviceId,
                            staff_id = :staffId,
                            start_time = :startTime,
                            end_time = :endTime,
                            unit_price = :unitPrice,
                            price_multiplier = :priceMultiplier,
                            line_total = :lineTotal,
                            sort_order = :sortOrder
                        WHERE id = :id
                        """, bindItem(item).addValue("id", item.getId()));
            }
            keepIds.add(item.getId());
        }
        if (keepIds.isEmpty()) {
            jdbc.update("DELETE FROM booking_items WHERE booking_id = :bookingId",
                    jdbc.params().addValue("bookingId", booking.getId()));
        } else {
            jdbc.update("DELETE FROM booking_items WHERE booking_id = :bookingId AND id NOT IN (:keepIds)",
                    jdbc.params().addValue("bookingId", booking.getId()).addValue("keepIds", new ArrayList<>(keepIds)));
        }
    }

    private void attachDetails(List<Booking> bookings) {
        if (bookings == null || bookings.isEmpty()) {
            return;
        }
        List<Long> bookingIds = new ArrayList<>();
        Set<Long> userIds = new LinkedHashSet<>();
        Set<Long> branchIds = new LinkedHashSet<>();
        Set<Long> businessIds = new LinkedHashSet<>();
        for (Booking booking : bookings) {
            bookingIds.add(booking.getId());
            addId(userIds, booking.getCustomerUser() == null ? null : booking.getCustomerUser().getId());
            addId(branchIds, booking.getBranch() == null ? null : booking.getBranch().getId());
            addId(businessIds, booking.getBusiness() == null ? null : booking.getBusiness().getId());
        }

        List<BookingItem> items = jdbc.findList(ITEM_SELECT, jdbc.params().addValue("ids", bookingIds), rows.bookingItem);
        Map<Long, List<BookingItem>> itemsByBooking = new LinkedHashMap<>();
        Set<Long> serviceIds = new LinkedHashSet<>();
        Set<Long> staffIds = new LinkedHashSet<>();
        for (BookingItem item : items) {
            Long bookingId = item.getBooking() == null ? null : item.getBooking().getId();
            itemsByBooking.computeIfAbsent(bookingId, key -> new ArrayList<>()).add(item);
            addId(serviceIds, item.getService() == null ? null : item.getService().getId());
            addId(staffIds, item.getStaff() == null ? null : item.getStaff().getId());
        }

        Map<Long, com.hourslot.model.Service> services = loadMap(SERVICE_SELECT, serviceIds, rows.service, com.hourslot.model.Service::getId);
        Map<Long, Staff> staff = loadMap(STAFF_SELECT, staffIds, rows.staff, Staff::getId);
        Map<Long, User> users = loadMap(USER_SELECT, userIds, rows.user, User::getId);
        Map<Long, Branch> branches = loadMap(BRANCH_SELECT, branchIds, rows.branch, Branch::getId);
        for (Branch branch : branches.values()) {
            addId(businessIds, branch.getBusiness() == null ? null : branch.getBusiness().getId());
        }
        Map<Long, Business> businesses = loadMap(BUSINESS_SELECT, businessIds, rows.business, Business::getId);

        for (Booking booking : bookings) {
            List<BookingItem> bookingItems = itemsByBooking.getOrDefault(booking.getId(), new ArrayList<>());
            for (BookingItem item : bookingItems) {
                item.setBooking(booking);
                if (item.getService() != null) {
                    com.hourslot.model.Service service = services.get(item.getService().getId());
                    if (service != null) {
                        item.setService(service);
                    }
                }
                if (item.getStaff() != null) {
                    Staff member = staff.get(item.getStaff().getId());
                    if (member != null) {
                        item.setStaff(member);
                    }
                }
            }
            booking.setItems(bookingItems);

            if (booking.getCustomerUser() != null) {
                User user = users.get(booking.getCustomerUser().getId());
                if (user != null) {
                    booking.setCustomerUser(user);
                }
            }
            if (booking.getBranch() != null) {
                Branch branch = branches.get(booking.getBranch().getId());
                if (branch != null) {
                    if (branch.getBusiness() != null) {
                        Business business = businesses.get(branch.getBusiness().getId());
                        if (business != null) {
                            branch.setBusiness(business);
                        }
                    }
                    booking.setBranch(branch);
                }
            }
            if (booking.getBusiness() != null) {
                Business business = businesses.get(booking.getBusiness().getId());
                if (business != null) {
                    booking.setBusiness(business);
                }
            }
        }
    }

    private List<Booking> findBookings(String sql, MapSqlParameterSource params) {
        List<Booking> bookings = jdbc.findList(sql, params, rows.booking);
        for (Booking booking : bookings) {
            booking.setItems(null);
        }
        return bookings;
    }

    private Optional<Booking> findOne(String sql, MapSqlParameterSource params) {
        return jdbc.findOne(sql, params, rows.booking).map(booking -> {
            booking.setItems(null);
            return booking;
        });
    }

    private MapSqlParameterSource bindRange(Long id, LocalDateTime start, LocalDateTime end,
                                            List<BookingStatus> statuses, String idKey) {
        return jdbc.params()
                .addValue(idKey, id)
                .addValue("start", JdbcSupport.ts(start))
                .addValue("end", JdbcSupport.ts(end))
                .addValue("statuses", JdbcSupport.statusNames(statuses));
    }

    private MapSqlParameterSource bind(Booking booking) {
        return jdbc.params()
                .addValue("publicCode", booking.getPublicCode())
                .addValue("customerUserId", booking.getCustomerUser() == null ? null : booking.getCustomerUser().getId())
                .addValue("organizationId", booking.getOrganization() == null ? null : booking.getOrganization().getId())
                .addValue("businessId", booking.getBusiness() == null ? null : booking.getBusiness().getId())
                .addValue("branchId", booking.getBranch() == null ? null : booking.getBranch().getId())
                .addValue("bookingTime", JdbcSupport.ts(booking.getBookingTime()))
                .addValue("endTime", JdbcSupport.ts(booking.getEndTime()))
                .addValue("status", booking.getStatus() == null ? null : booking.getStatus().name())
                .addValue("totalPrice", booking.getTotalPrice() == null ? BigDecimal.ZERO : booking.getTotalPrice())
                .addValue("currency", booking.getCurrency())
                .addValue("paymentStatus", booking.getPaymentStatus())
                .addValue("paymentMethod", booking.getPaymentMethod())
                .addValue("clientNotes", booking.getClientNotes())
                .addValue("internalNotes", booking.getInternalNotes())
                .addValue("source", booking.getSource())
                .addValue("customerPackageId", booking.getCustomerPackage() == null ? null : booking.getCustomerPackage().getId())
                .addValue("version", booking.getVersion())
                .addValue("createdAt", JdbcSupport.ts(booking.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(booking.getUpdatedAt()));
    }

    private MapSqlParameterSource bindItem(BookingItem item) {
        return jdbc.params()
                .addValue("bookingId", item.getBooking() == null ? null : item.getBooking().getId())
                .addValue("serviceId", item.getService() == null ? null : item.getService().getId())
                .addValue("staffId", item.getStaff() == null ? null : item.getStaff().getId())
                .addValue("startTime", JdbcSupport.ts(item.getStartTime()))
                .addValue("endTime", JdbcSupport.ts(item.getEndTime()))
                .addValue("unitPrice", item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice())
                .addValue("priceMultiplier", item.getPriceMultiplier() == null ? BigDecimal.ONE : item.getPriceMultiplier())
                .addValue("lineTotal", item.getLineTotal() == null ? BigDecimal.ZERO : item.getLineTotal())
                .addValue("sortOrder", item.getSortOrder() == null ? 0 : item.getSortOrder());
    }

    private <T> Map<Long, T> loadMap(String sql, Set<Long> ids, RowMapper<T> mapper, Function<T, Long> idFn) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<T> rowsFound = jdbc.findList(sql, jdbc.params().addValue("ids", new ArrayList<>(ids)), mapper);
        Map<Long, T> map = new LinkedHashMap<>();
        for (T row : rowsFound) {
            Long id = idFn.apply(row);
            if (id != null) {
                map.put(id, row);
            }
        }
        return map;
    }

    private static void addId(Collection<Long> ids, Long id) {
        if (id != null) {
            ids.add(id);
        }
    }
}
