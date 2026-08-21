package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.AuditEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AuditEventRepository {

    private static final String SELECT = """
            SELECT id, actor_user_id, organization_id, business_id, action, entity_type, entity_id,
                   before_state, after_state, ip_address, user_agent, created_at
            FROM audit_events
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public AuditEventRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public AuditEvent save(AuditEvent event) {
        if (event.getId() == null) {
            event.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO audit_events (actor_user_id, organization_id, business_id, action, entity_type, entity_id,
                                              before_state, after_state, ip_address, user_agent, created_at)
                    VALUES (:actorUserId, :organizationId, :businessId, :action, :entityType, :entityId,
                            :beforeState, :afterState, :ipAddress, :userAgent, :createdAt)
                    """, bind(event));
            event.setId(id);
            return event;
        }
        jdbc.update("""
                UPDATE audit_events SET actor_user_id = :actorUserId, organization_id = :organizationId,
                    business_id = :businessId, action = :action, entity_type = :entityType, entity_id = :entityId,
                    before_state = :beforeState, after_state = :afterState, ip_address = :ipAddress,
                    user_agent = :userAgent
                WHERE id = :id
                """, bind(event).addValue("id", event.getId()));
        return event;
    }

    public List<AuditEvent> findAllByOrderByCreatedAtDesc() {
        return jdbc.findList(SELECT + " ORDER BY created_at DESC", jdbc.params(), rows.auditEvent);
    }

    private MapSqlParameterSource bind(AuditEvent event) {
        return jdbc.params()
                .addValue("actorUserId", event.getActor() == null ? null : event.getActor().getId())
                .addValue("organizationId", event.getOrganization() == null ? null : event.getOrganization().getId())
                .addValue("businessId", event.getBusiness() == null ? null : event.getBusiness().getId())
                .addValue("action", event.getAction())
                .addValue("entityType", event.getEntityType())
                .addValue("entityId", event.getEntityId())
                .addValue("beforeState", jdbc.jsonb(event.getBeforeState()))
                .addValue("afterState", jdbc.jsonb(event.getAfterState()))
                .addValue("ipAddress", event.getIpAddress())
                .addValue("userAgent", event.getUserAgent())
                .addValue("createdAt", JdbcSupport.ts(event.getCreatedAt()));
    }
}
