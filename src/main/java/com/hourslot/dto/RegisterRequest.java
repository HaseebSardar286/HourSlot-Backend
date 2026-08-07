package com.hourslot.dto;

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

    private String role; // CUSTOMER, BUSINESS_ADMIN, etc. Defaults to CUSTOMER

    private String firstName;
    private String lastName;
    private String phoneNumber;

    // Optional business onboarding fields (used when role = BUSINESS_ADMIN)
    private String businessName;
    private String businessCategory;
    private String businessDescription;
}

