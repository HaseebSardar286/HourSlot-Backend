package com.duemet.marketing;

import com.duemet.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A business's own customer records: who booked, how often, what they spent, private notes and tags.
 * A business only ever sees customers who have booked with it.
 */
@Service
public class CustomerRecordsService {

    static final int MAX_TAGS = 10;

    private final JdbcSupport jdbc;

    public CustomerRecordsService(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(Long businessId) {
        List<Map<String, Object>> rows = jdbc.jdbc().queryForList("""
                SELECT u.id AS customer_id, u.email, u.first_name, u.last_name, u.phone_number,
                       COUNT(b.id) AS booking_count,
                       COUNT(b.id) FILTER (WHERE b.status = 'COMPLETED') AS completed_count,
                       COUNT(b.id) FILTER (WHERE b.status = 'NO_SHOW') AS no_show_count,
                       COALESCE(SUM(b.total_price) FILTER (WHERE b.status = 'COMPLETED'), 0) AS total_spent,
                       MAX(b.booking_time) FILTER (WHERE b.status = 'COMPLETED') AS last_visit_at,
                       MIN(b.booking_time) FILTER (WHERE b.status IN ('PENDING','CONFIRMED') AND b.booking_time > NOW()) AS next_booking_at,
                       (SELECT STRING_AGG(t.tag, ',' ORDER BY t.tag) FROM customer_business_tags t
                          WHERE t.business_id = :b AND t.customer_user_id = u.id) AS tags
                FROM bookings b
                JOIN users u ON u.id = b.customer_user_id
                WHERE b.business_id = :b AND b.deleted_at IS NULL
                GROUP BY u.id, u.email, u.first_name, u.last_name, u.phone_number
                ORDER BY MAX(b.booking_time) DESC NULLS LAST
                """, Map.of("b", businessId));
        rows.forEach(row -> row.put("tags", splitTags(row.get("tags"))));
        return rows;
    }

    public Map<String, Object> detail(Long businessId, Long customerId) {
        requireCustomerOf(businessId, customerId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customer", jdbc.jdbc().queryForMap("""
                SELECT id AS customer_id, email, first_name, last_name, phone_number FROM users WHERE id = :c
                """, Map.of("c", customerId)));
        result.put("bookings", jdbc.jdbc().queryForList("""
                SELECT b.id, b.public_code, b.booking_time, b.status, b.total_price, b.currency, b.payment_status,
                       (SELECT s.name FROM booking_items i JOIN services s ON s.id = i.service_id
                          WHERE i.booking_id = b.id ORDER BY i.sort_order LIMIT 1) AS service_name
                FROM bookings b
                WHERE b.business_id = :b AND b.customer_user_id = :c AND b.deleted_at IS NULL
                ORDER BY b.booking_time DESC LIMIT 100
                """, Map.of("b", businessId, "c", customerId)));
        result.put("notes", jdbc.jdbc().queryForList("""
                SELECT n.id, n.note, n.created_at, a.first_name AS author_first_name
                FROM customer_business_notes n LEFT JOIN users a ON a.id = n.author_user_id
                WHERE n.business_id = :b AND n.customer_user_id = :c ORDER BY n.created_at DESC
                """, Map.of("b", businessId, "c", customerId)));
        result.put("tags", tags(businessId, customerId));
        return result;
    }

    @Transactional
    public void addNote(Long businessId, Long customerId, Long authorId, String note) {
        requireCustomerOf(businessId, customerId);
        String text = note == null ? "" : note.trim();
        if (text.isEmpty() || text.length() > 2000) {
            throw new IllegalArgumentException("A note must be 1–2000 characters.");
        }
        jdbc.update("""
                INSERT INTO customer_business_notes (business_id, customer_user_id, author_user_id, note)
                VALUES (:b, :c, :a, :n)
                """, jdbc.params().addValue("b", businessId).addValue("c", customerId)
                .addValue("a", authorId).addValue("n", text));
    }

    public void deleteNote(Long businessId, Long noteId) {
        if (jdbc.update("DELETE FROM customer_business_notes WHERE id = :id AND business_id = :b",
                jdbc.params().addValue("id", noteId).addValue("b", businessId)) == 0) {
            throw new RuntimeException("Note not found.");
        }
    }

    /** Replace the customer's tags (e.g. "VIP", "Pays late", "Has dog"). */
    @Transactional
    public List<String> setTags(Long businessId, Long customerId, List<String> raw) {
        requireCustomerOf(businessId, customerId);
        Set<String> clean = new LinkedHashSet<>();
        for (String tag : raw == null ? List.<String>of() : raw) {
            String t = tag == null ? "" : tag.trim().replaceAll("\\s+", " ");
            if (t.isEmpty()) continue;
            if (t.length() > 40) {
                throw new IllegalArgumentException("Tags can be at most 40 characters.");
            }
            clean.add(t);
        }
        if (clean.size() > MAX_TAGS) {
            throw new IllegalArgumentException("Use at most " + MAX_TAGS + " tags per customer.");
        }
        jdbc.update("DELETE FROM customer_business_tags WHERE business_id = :b AND customer_user_id = :c",
                jdbc.params().addValue("b", businessId).addValue("c", customerId));
        for (String tag : clean) {
            jdbc.update("""
                    INSERT INTO customer_business_tags (business_id, customer_user_id, tag) VALUES (:b, :c, :t)
                    ON CONFLICT DO NOTHING
                    """, jdbc.params().addValue("b", businessId).addValue("c", customerId).addValue("t", tag));
        }
        return tags(businessId, customerId);
    }

    private List<String> tags(Long businessId, Long customerId) {
        return jdbc.jdbc().queryForList("""
                SELECT tag FROM customer_business_tags WHERE business_id = :b AND customer_user_id = :c ORDER BY tag
                """, Map.of("b", businessId, "c", customerId), String.class);
    }

    private void requireCustomerOf(Long businessId, Long customerId) {
        if (!jdbc.exists("SELECT COUNT(*) FROM bookings WHERE business_id = :b AND customer_user_id = :c AND deleted_at IS NULL",
                jdbc.params().addValue("b", businessId).addValue("c", customerId))) {
            throw new SecurityException("This person is not one of your customers.");
        }
    }

    private static List<String> splitTags(Object value) {
        if (value == null || value.toString().isBlank()) {
            return List.of();
        }
        return new ArrayList<>(List.of(value.toString().split(",")));
    }

    static String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
    }
}
