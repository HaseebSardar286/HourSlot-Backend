package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hourslot.dto.CustomerView;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_bookings_branch_time", columnList = "branch_id, booking_time")
})
@SQLRestriction("deleted_at IS NULL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "items", "customerUser", "organization"})
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_code", unique = true)
    private String publicCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_user_id", nullable = false)
    private User customerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id")
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @NotNull
    @Column(name = "booking_time", nullable = false)
    private LocalDateTime bookingTime;

    @NotNull
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "payment_status")
    private String paymentStatus;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "client_notes", columnDefinition = "TEXT")
    private String clientNotes;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(nullable = false)
    @Builder.Default
    private String source = "MARKETPLACE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_package_id")
    private CustomerPackage customerPackage;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BookingItem> items = new ArrayList<>();

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
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

    @PrePersist
    protected void onCreate() {
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

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
