package com.hourslot.model;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberRole {

    private Long id;

    private User user;

    private Role role;

    private Organization organization;

    private Business business;

    private Branch branch;

    private Staff staff;

    private LocalDateTime grantedAt;

    private User grantedBy;

    private LocalDateTime expiresAt;

    private LocalDateTime deletedAt;

    public void onCreate() {
        if (this.grantedAt == null) {
            this.grantedAt = LocalDateTime.now();
        }
    }
}
