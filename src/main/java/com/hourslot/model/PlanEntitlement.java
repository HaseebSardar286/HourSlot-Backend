package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "plan_entitlements", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"plan_id", "entitlement_code"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "plan"})
public class PlanEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "entitlement_code", nullable = false)
    private String entitlementCode;

    @Column(name = "value_type", nullable = false)
    private String valueType;

    @Column(nullable = false)
    private String value;
}
