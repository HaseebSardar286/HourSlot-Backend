package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hourslot.dto.CustomerView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "customerUser"})
public class Review {

    private Long id;

    private User customerUser;

    private Business business;

    private Booking booking;

    @Min(1)
    @Max(5)
    private int rating;

    private String comment;

    private String ownerReply;

    private LocalDateTime ownerRepliedAt;

    @Builder.Default
    private boolean visible = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @JsonProperty("customer")
    public CustomerView getCustomer() {
        if (customerUser == null) {
            return null;
        }
        return new CustomerView(customerUser.getId(), customerUser);
    }

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
