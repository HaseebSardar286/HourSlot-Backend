package com.hourslot.model;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    private Long id;

    private String tokenHash;

    private User user;

    private LocalDateTime expiresAt;

    @Builder.Default
    private boolean used = false;

    private LocalDateTime createdAt;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
