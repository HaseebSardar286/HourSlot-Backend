package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CustomerPackage {

    private Long id;

    private User customerUser;

    private ServicePackage servicePackage;

    private Business business;

    private int sessionsRemaining;

    private LocalDateTime expiresAt;

    private String status;

    private Long purchasePaymentId;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
