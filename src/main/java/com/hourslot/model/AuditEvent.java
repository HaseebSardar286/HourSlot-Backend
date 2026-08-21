package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AuditEvent {

    private Long id;

    @JsonProperty("user")
    private User actor;

    private Organization organization;

    private Business business;

    private String action;

    private String entityType;

    private Long entityId;

    private Map<String, Object> beforeState;

    private Map<String, Object> afterState;

    private String ipAddress;

    private String userAgent;

    private LocalDateTime createdAt;

    @JsonProperty("entity")
    public String getEntity() {
        return entityType;
    }

    @JsonProperty("timestamp")
    public LocalDateTime getTimestamp() {
        return createdAt;
    }

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
