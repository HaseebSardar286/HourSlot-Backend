package com.duemet.organization.services;

import com.duemet.identity.model.User;
import com.duemet.organization.model.AuditEvent;
import com.duemet.organization.model.Business;
import com.duemet.organization.model.Organization;
import com.duemet.organization.repository.AuditEventRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public void log(User actor, Business business, String action, String entityType, Long entityId, String details) {
        Organization organization = business == null ? null : business.getOrganization();
        AuditEvent event = AuditEvent.builder()
                .actor(actor)
                .organization(organization)
                .business(business)
                .action(details == null || details.isBlank() ? action : action + " - " + details)
                .entityType(entityType)
                .entityId(entityId)
                .build();
        auditEventRepository.save(event);
    }
}
