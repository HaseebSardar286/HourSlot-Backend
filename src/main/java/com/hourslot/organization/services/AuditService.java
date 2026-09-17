package com.hourslot.organization.services;

import com.hourslot.identity.model.User;
import com.hourslot.organization.model.AuditEvent;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.model.Organization;
import com.hourslot.organization.repository.AuditEventRepository;
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
