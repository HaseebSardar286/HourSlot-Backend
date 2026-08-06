package com.hourslot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String refreshToken;
    private final String type = "Bearer";
    private Long id;
    private String email;
    private String role;
    private String firstName;
    private String lastName;
    private List<String> authorities;
}
