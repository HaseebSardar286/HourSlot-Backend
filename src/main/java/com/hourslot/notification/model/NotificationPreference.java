package com.hourslot.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.hourslot.identity.model.User;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    private User user;

    private String channel;

    private String eventType;

    @Builder.Default
    private boolean enabled = true;
}
