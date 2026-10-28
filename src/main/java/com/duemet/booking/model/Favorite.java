package com.duemet.booking.model;

import lombok.*;

import java.time.LocalDateTime;
import com.duemet.identity.model.User;
import com.duemet.organization.model.Business;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Favorite {

    private Long id;

    private User customerUser;

    private Business business;

    private LocalDateTime createdAt;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
