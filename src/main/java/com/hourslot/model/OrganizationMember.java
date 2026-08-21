package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class OrganizationMember {

    private Long id;

    private Organization organization;

    private User user;

    @Builder.Default
    private String status = "ACTIVE";

    private User invitedBy;

    private LocalDateTime joinedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.joinedAt == null && "ACTIVE".equals(this.status)) {
            this.joinedAt = LocalDateTime.now();
        }
    }

    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
