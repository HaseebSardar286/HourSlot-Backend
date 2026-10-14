package com.hourslot.identity.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String refreshToken;
    @Builder.Default
    private String type = "Bearer";
    private Long id;
    private String email;
    private String role;
    private String firstName;
    private String lastName;
    private List<String> authorities;
    private String listingMode;
    private String providerType;
    private String providerIntent;
    private String onboardingState;
    private Boolean providerWorkspace;
    private Boolean isOnline;
}
