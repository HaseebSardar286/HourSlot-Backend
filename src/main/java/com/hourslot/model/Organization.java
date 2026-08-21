package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "members"})
public class Organization {

    private Long id;

    private String name;

    private String slug;

    private String billingEmail;

    @Builder.Default
    private String status = "ACTIVE";

    private String stripeCustomerId;

    private String stripeConnectAccountId;

    @Builder.Default
    private String defaultCurrency = "USD";

    private String countryCode;

    private String timezone;

    @Builder.Default
    private List<OrganizationMember> members = new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "ACTIVE";
        }
        if (this.defaultCurrency == null) {
            this.defaultCurrency = "USD";
        }
    }

    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
