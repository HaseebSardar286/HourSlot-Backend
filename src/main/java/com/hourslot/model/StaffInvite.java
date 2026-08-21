package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "tokenHash"})
public class StaffInvite {

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String EXPIRED = "EXPIRED";
    public static final String REVOKED = "REVOKED";

    private Long id;

    private Organization organization;

    private Business business;

    private Branch branch;

    private String email;

    private String displayName;

    private String designation;

    private String tokenHash;

    @Builder.Default
    private String status = PENDING;

    @JsonIgnoreProperties({"passwordHash", "hibernateLazyInitializer", "handler"})
    private User invitedBy;

    private LocalDateTime expiresAt;

    private LocalDateTime acceptedAt;

    private LocalDateTime createdAt;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = PENDING;
        }
    }
}
