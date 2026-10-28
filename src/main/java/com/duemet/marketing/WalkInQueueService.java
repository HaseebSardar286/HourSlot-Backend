package com.duemet.marketing;

import com.duemet.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walk-in queue for customers without a booking: ticket numbers restart each day, the provider calls
 * the next person, and a public screen shows who is being served and the estimated wait.
 */
@Service
public class WalkInQueueService {

    /** Used for the wait estimate until today's queue has finished tickets to learn from. */
    static final int DEFAULT_SERVICE_MINUTES = 20;
    private static final Set<String> TRANSITIONS = Set.of("SERVING", "DONE", "LEFT");

    private final JdbcSupport jdbc;

    public WalkInQueueService(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> add(Long businessId, String name, String phone, Long serviceId) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty() || cleanName.length() > 120) {
            throw new IllegalArgumentException("Enter the customer's name (up to 120 characters).");
        }
        if (serviceId != null && !jdbc.exists("SELECT COUNT(*) FROM services WHERE id = :s AND business_id = :b",
                jdbc.params().addValue("s", serviceId).addValue("b", businessId))) {
            throw new IllegalArgumentException("Choose one of your own services.");
        }
        LocalDate today = LocalDate.now();
        // Serialise ticket numbering per business per day so two walk-ins never share a number.
        jdbc.count("SELECT COUNT(*) FROM (SELECT pg_advisory_xact_lock(hashtext(:k))) l",
                jdbc.params().addValue("k", "queue:" + businessId + ":" + today));
        Long id = jdbc.insert("""
                INSERT INTO queue_entries (business_id, queue_date, ticket_number, customer_name, phone, service_id)
                VALUES (:b, :d,
                        (SELECT COALESCE(MAX(ticket_number), 0) + 1 FROM queue_entries WHERE business_id = :b AND queue_date = :d),
                        :name, :phone, :service)
                """, jdbc.params().addValue("b", businessId).addValue("d", today).addValue("name", cleanName)
                .addValue("phone", phone == null || phone.isBlank() ? null : phone.trim())
                .addValue("service", serviceId));
        return jdbc.jdbc().queryForMap("SELECT * FROM queue_entries WHERE id = :id", Map.of("id", id));
    }

    public List<Map<String, Object>> today(Long businessId) {
        return jdbc.jdbc().queryForList("""
                SELECT q.*, s.name AS service_name FROM queue_entries q
                LEFT JOIN services s ON s.id = q.service_id
                WHERE q.business_id = :b AND q.queue_date = :d
                ORDER BY CASE q.status WHEN 'SERVING' THEN 0 WHEN 'WAITING' THEN 1 ELSE 2 END, q.ticket_number
                """, Map.of("b", businessId, "d", LocalDate.now()));
    }

    /** WAITING → SERVING → DONE; anyone not yet done can be marked LEFT. */
    public void setStatus(Long businessId, Long entryId, String rawStatus) {
        String status = rawStatus == null ? "" : rawStatus.trim().toUpperCase();
        if (!TRANSITIONS.contains(status)) {
            throw new IllegalArgumentException("Status must be SERVING, DONE or LEFT.");
        }
        String allowedFrom = switch (status) {
            case "SERVING" -> "'WAITING'";
            case "DONE" -> "'SERVING'";
            default -> "'WAITING','SERVING'";
        };
        int updated = jdbc.update("""
                UPDATE queue_entries SET status = :status,
                    called_at = CASE WHEN :status = 'SERVING' THEN NOW() ELSE called_at END,
                    finished_at = CASE WHEN :status IN ('DONE','LEFT') THEN NOW() ELSE finished_at END
                WHERE id = :id AND business_id = :b AND status IN (""" + allowedFrom + ")",
                jdbc.params().addValue("status", status).addValue("id", entryId).addValue("b", businessId));
        if (updated == 0) {
            throw new IllegalStateException("That ticket can't be moved to " + status.toLowerCase() + ".");
        }
    }

    /** Call the next waiting ticket (lowest number). */
    @Transactional
    public Map<String, Object> callNext(Long businessId) {
        List<Long> next = jdbc.jdbc().queryForList("""
                SELECT id FROM queue_entries WHERE business_id = :b AND queue_date = :d AND status = 'WAITING'
                ORDER BY ticket_number LIMIT 1 FOR UPDATE SKIP LOCKED
                """, Map.of("b", businessId, "d", LocalDate.now()), Long.class);
        if (next.isEmpty()) {
            throw new IllegalStateException("Nobody is waiting.");
        }
        setStatus(businessId, next.get(0), "SERVING");
        return jdbc.jdbc().queryForMap("SELECT * FROM queue_entries WHERE id = :id", Map.of("id", next.get(0)));
    }

    /** What the public screen shows. First names only, so the screen does not expose full names or phones. */
    public Map<String, Object> publicBoard(Long businessId, String businessName) {
        List<Map<String, Object>> rows = jdbc.jdbc().queryForList("""
                SELECT ticket_number, customer_name, status FROM queue_entries
                WHERE business_id = :b AND queue_date = :d AND status IN ('WAITING','SERVING')
                ORDER BY ticket_number
                """, Map.of("b", businessId, "d", LocalDate.now()));
        Double avgMinutes = jdbc.jdbc().queryForObject("""
                SELECT AVG(EXTRACT(EPOCH FROM (finished_at - called_at)) / 60.0) FROM queue_entries
                WHERE business_id = :b AND queue_date = :d AND status = 'DONE' AND called_at IS NOT NULL
                """, Map.of("b", businessId, "d", LocalDate.now()), Double.class);
        List<Map<String, Object>> serving = rows.stream().filter(r -> "SERVING".equals(r.get("status")))
                .map(WalkInQueueService::publicTicket).toList();
        List<Map<String, Object>> waiting = rows.stream().filter(r -> "WAITING".equals(r.get("status")))
                .map(WalkInQueueService::publicTicket).toList();
        double perPerson = avgMinutes == null ? DEFAULT_SERVICE_MINUTES : Math.max(5, avgMinutes);
        int servers = Math.max(1, serving.size());
        Map<String, Object> board = new LinkedHashMap<>();
        board.put("businessName", businessName);
        board.put("serving", serving);
        board.put("waiting", waiting);
        board.put("waitingCount", waiting.size());
        board.put("estimatedWaitMinutes", (int) Math.round(waiting.size() * perPerson / servers));
        return board;
    }

    static Map<String, Object> publicTicket(Map<String, Object> row) {
        String name = String.valueOf(row.get("customer_name")).trim();
        String[] parts = name.split("\\s+");
        String display = parts.length > 1 ? parts[0] + " " + parts[parts.length - 1].charAt(0) + "." : parts[0];
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("ticket", row.get("ticket_number"));
        t.put("name", display);
        return t;
    }
}
