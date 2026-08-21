package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    private Long id;

    @NotBlank
    @Email
    private String email;

    @JsonIgnore
    private String passwordHash;

    private String phoneNumber;

    @Builder.Default
    private String status = "ACTIVE";

    private LocalDateTime emailVerifiedAt;

    private String locale;

    private String timezone;

    private LocalDateTime lastLoginAt;

    private String firstName;

    private String lastName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @JsonIgnore
    private LocalDateTime deletedAt;

    /** App-facing role alias used by JWT and existing UI (not a DB column). */
    private UserRole role;

    @JsonIgnore
    public String getPassword() {
        return passwordHash;
    }

    public void setPassword(String password) {
        this.passwordHash = password;
    }

    @JsonProperty("active")
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public void setActive(boolean active) {
        this.status = active ? "ACTIVE" : "DISABLED";
    }

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "ACTIVE";
        }
    }

    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
