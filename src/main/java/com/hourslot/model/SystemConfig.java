package com.hourslot.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "default_commission_rate", nullable = false)
    private double defaultCommissionRate; // e.g. 10.0 for 10%

    @Column(name = "supported_currencies")
    private String supportedCurrencies; // Comma-separated, e.g. "USD,PKR,AED"

    @Column(name = "is_registration_open", nullable = false)
    private boolean registrationOpen;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.defaultCommissionRate == 0) {
            this.defaultCommissionRate = 10.0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
