package com.hourslot.notification.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import com.hourslot.identity.model.User;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Notification {

    private Long id;

    @JsonIgnore
    private User user;

    @Builder.Default
    private String channel = "IN_APP";

    private String title;

    private String body;

    private String eventType;

    private Long referenceId;

    @Builder.Default
    private boolean read = false;

    private LocalDateTime sentAt;

    private LocalDateTime createdAt;

    @JsonProperty("message")
    public String getMessage() {
        return body;
    }

    public void setMessage(String message) {
        this.body = message;
    }

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.channel == null) {
            this.channel = "IN_APP";
        }
        if (this.sentAt == null) {
            this.sentAt = this.createdAt;
        }
    }
}
