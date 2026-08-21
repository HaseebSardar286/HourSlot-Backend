package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "plan"})
public class PlanEntitlement {

    private Long id;

    private SubscriptionPlan plan;

    private String entitlementCode;

    private String valueType;

    private String value;
}
