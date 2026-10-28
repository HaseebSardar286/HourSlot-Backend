package com.duemet.identity.dto;

import lombok.Data;

@Data
public class TokenRefreshRequest {
    /** Optional: browsers send the httpOnly refresh cookie instead. */
    private String refreshToken;
}
