package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Booking;
import com.hourslot.model.Business;
import com.hourslot.model.Review;
import com.hourslot.model.User;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class ReviewRepository {

    private static final String SELECT = """
            SELECT id, customer_user_id, business_id, booking_id, rating, comment, owner_reply, owner_replied_at,
                   is_visible, created_at, updated_at, deleted_at
            FROM reviews
            """;

    private static final String USER_SELECT = """
            SELECT id, email, password_hash, phone_number, status, email_verified_at, locale, timezone,
                   last_login_at, first_name, last_name, created_at, updated_at, deleted_at
            FROM users WHERE id IN (:ids)
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public ReviewRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Review save(Review review) {
        if (review.getId() == null) {
            review.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO reviews (
                        customer_user_id, business_id, booking_id, rating, comment, owner_reply, owner_replied_at,
                        is_visible, created_at, updated_at)
                    VALUES (
                        :customerUserId, :businessId, :bookingId, :rating, :comment, :ownerReply, :ownerRepliedAt,
                        :visible, :createdAt, :updatedAt)
                    """, bind(review));
            review.setId(id);
            return review;
        }
        review.onUpdate();
        jdbc.update("""
                UPDATE reviews SET
                    customer_user_id = :customerUserId,
                    business_id = :businessId,
                    booking_id = :bookingId,
                    rating = :rating,
                    comment = :comment,
                    owner_reply = :ownerReply,
                    owner_replied_at = :ownerRepliedAt,
                    is_visible = :visible,
                    updated_at = :updatedAt
                WHERE id = :id
                """, bind(review).addValue("id", review.getId()));
        return review;
    }

    public List<Review> findByBusinessOrderByCreatedAtDesc(Business business) {
        List<Review> reviews = jdbc.findList(
                SELECT + " WHERE business_id = :businessId AND deleted_at IS NULL ORDER BY created_at DESC",
                jdbc.params().addValue("businessId", business.getId()),
                rows.review);
        attachCustomers(reviews);
        return reviews;
    }

    public boolean existsByBooking(Booking booking) {
        if (booking == null || booking.getId() == null) {
            return false;
        }
        return jdbc.exists(
                "SELECT COUNT(*) FROM reviews WHERE booking_id = :bookingId AND deleted_at IS NULL",
                jdbc.params().addValue("bookingId", booking.getId()));
    }

    private void attachCustomers(List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return;
        }
        Set<Long> userIds = new LinkedHashSet<>();
        for (Review review : reviews) {
            if (review.getCustomerUser() != null && review.getCustomerUser().getId() != null) {
                userIds.add(review.getCustomerUser().getId());
            }
        }
        if (userIds.isEmpty()) {
            return;
        }
        List<User> users = jdbc.findList(USER_SELECT, jdbc.params().addValue("ids", new ArrayList<>(userIds)), rows.user);
        Map<Long, User> byId = new LinkedHashMap<>();
        for (User user : users) {
            byId.put(user.getId(), user);
        }
        for (Review review : reviews) {
            if (review.getCustomerUser() != null) {
                User user = byId.get(review.getCustomerUser().getId());
                if (user != null) {
                    review.setCustomerUser(user);
                }
            }
        }
    }

    private MapSqlParameterSource bind(Review review) {
        return jdbc.params()
                .addValue("customerUserId", review.getCustomerUser() == null ? null : review.getCustomerUser().getId())
                .addValue("businessId", review.getBusiness() == null ? null : review.getBusiness().getId())
                .addValue("bookingId", review.getBooking() == null ? null : review.getBooking().getId())
                .addValue("rating", review.getRating())
                .addValue("comment", review.getComment())
                .addValue("ownerReply", review.getOwnerReply())
                .addValue("ownerRepliedAt", JdbcSupport.ts(review.getOwnerRepliedAt()))
                .addValue("visible", review.isVisible())
                .addValue("createdAt", JdbcSupport.ts(review.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(review.getUpdatedAt()));
    }
}
