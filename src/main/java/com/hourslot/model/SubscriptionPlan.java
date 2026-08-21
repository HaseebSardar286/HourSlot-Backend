package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "entitlements"})
public class SubscriptionPlan {

    private Long id;

    private String code;

    private String name;

    private String billingInterval;

    private BigDecimal price;

    @Builder.Default
    private String currency = "USD";

    private String stripePriceId;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private int sortOrder = 0;

    private Map<String, Object> features;

    @Builder.Default
    private List<PlanEntitlement> entitlements = new ArrayList<>();
}
