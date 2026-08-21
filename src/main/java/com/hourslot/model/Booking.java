package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hourslot.dto.CustomerView;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "items", "customerUser", "organization"})
public class Booking {

    private Long id;

    private String publicCode;

    private User customerUser;

    private Organization organization;

    private Business business;

    private Branch branch;

    @NotNull
    private LocalDateTime bookingTime;

    @NotNull
    private LocalDateTime endTime;

    @NotNull
    private BookingStatus status;

    private BigDecimal totalPrice;

    @Builder.Default
    private String currency = "USD";

    private String paymentStatus;

    private String paymentMethod;

    private String clientNotes;

    private String internalNotes;

    @Builder.Default
    private String source = "MARKETPLACE";

    private CustomerPackage customerPackage;

    @Builder.Default
    private List<BookingItem> items = new ArrayList<>();

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @JsonIgnore
    private LocalDateTime deletedAt;

    @JsonProperty("customer")
    public CustomerView getCustomer() {
        if (customerUser == null) {
            return null;
        }
        return new CustomerView(customerUser.getId(), customerUser);
    }

    @JsonProperty("service")
    public Service getService() {
        BookingItem item = primaryItem();
        return item == null ? null : item.getService();
    }

    @JsonProperty("staff")
    public Staff getStaff() {
        BookingItem item = primaryItem();
        return item == null ? null : item.getStaff();
    }

    @JsonProperty("price")
    public double getPrice() {
        return totalPrice == null ? 0.0 : totalPrice.doubleValue();
    }

    public void setPrice(double price) {
        this.totalPrice = BigDecimal.valueOf(price);
    }

    @JsonIgnore
    public BookingItem primaryItem() {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
                .min(Comparator.comparingInt(item -> item.getSortOrder() == null ? 0 : item.getSortOrder()))
                .orElse(items.get(0));
    }

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.currency == null) {
            this.currency = "USD";
        }
        if (this.source == null) {
            this.source = "MARKETPLACE";
        }
        if (this.publicCode == null) {
            this.publicCode = "TMP-" + System.nanoTime();
        }
    }

    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
