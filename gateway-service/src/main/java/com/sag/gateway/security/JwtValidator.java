package com.sag.gateway.security;

import com.sag.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Validates JWTs issued by auth-service. This class NEVER creates a
 * token - only auth-service's JwtTokenProvider does that. That
 * separation is the whole point of "loosely coupled" auth here: the
 * gateway doesn't need to know anything about how users are stored,
 * hashed, or authenticated - it only needs the shared signing secret to
 * confirm a token is genuine and unexpired.
 */
@Component
public class JwtValidator {

    private final SecretKey signingKey;

    public JwtValidator(JwtProperties jwtProperties) {
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the token's claims if it's valid (correct signature, not
     * expired). Throws JwtException (or a subclass) otherwise - callers
     * should treat any exception here as "reject the request with 401".
     */
    public Claims validateAndExtractClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
