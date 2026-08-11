package com.hourslot.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenTypeTest {

    private final String secret = "OWFiYzEyMzRkZWY1Njc4OWFiYzEyMzRkZWY1Njc4OWFiYzEyMzRkZWY1Njc4OWFiYzEyMzRkZWY1Njc4OWFiYzEyMw==";

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    @Test
    void refreshTokenClaimIsDistinctFromAccess() {
        String refresh = Jwts.builder()
                .subject("user@example.com")
                .claim("tokenType", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key())
                .compact();

        String access = Jwts.builder()
                .subject("user@example.com")
                .claim("tokenType", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key())
                .compact();

        String refreshType = Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(refresh).getPayload().get("tokenType", String.class);
        String accessType = Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(access).getPayload().get("tokenType", String.class);

        assertEquals("refresh", refreshType);
        assertEquals("access", accessType);
        assertNotEquals(refreshType, accessType);
    }
}
