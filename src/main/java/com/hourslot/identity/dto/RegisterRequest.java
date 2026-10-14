package com.hourslot.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(max = 50)
    @Email
    private String email;

    @NotBlank
    @Size(min = 6, max = 40)
    private String password;

    /** CUSTOMER | BUSINESS_OWNER — legacy; prefer accountType. */
    private String role;

    /**
     * CUSTOMER | INDIVIDUAL | BUSINESS.
     * INDIVIDUAL creates a customer dual-identity account with providerIntent=INDIVIDUAL.
     */
    private String accountType;

    private String firstName;
    private String lastName;
    private String phoneNumber;

    // Business onboarding (accountType = BUSINESS)
    private String businessName;
    private String businessCategory;
    private String businessDescription;
    private String registrationNumber;
    private String branchName;
    private String address;
    private Double latitude;
    private Double longitude;

    // Individual listing (accountType = INDIVIDUAL)
    private String displayName;
    private String slug;
    private String bio;
    private String serviceMode;
    private Integer yearsExperience;
    private Long primaryCategoryId;
    private String countryCode;
    private String region;
    private String city;
    private String timezone;
    private String currency;
}
