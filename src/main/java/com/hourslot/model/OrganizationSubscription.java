package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class OrganizationSubscription {

    private Long id;

    private Organization organization;

    private SubscriptionPlan plan;

    private String status;

    private String stripeSubscriptionId;

    private LocalDateTime currentPeriodStart;

    private LocalDateTime currentPeriodEnd;

    @Builder.Default
    private boolean cancelAtPeriodEnd = false;

    private Map<String, Object> entitlementOverrides;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "ACTIVE";
        }
    }

    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
