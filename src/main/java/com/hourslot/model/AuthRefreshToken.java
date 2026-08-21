package com.hourslot.model;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRefreshToken {

    private Long id;

    private User user;

    private String tokenHash;

    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    private LocalDateTime createdAt;

    private String userAgent;

    private String ipAddress;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return revokedAt == null && LocalDateTime.now().isBefore(expiresAt);
    }
}
