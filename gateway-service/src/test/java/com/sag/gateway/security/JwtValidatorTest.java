package com.sag.gateway.security;

import com.sag.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidatorTest {

    private static final String SECRET =
            "TestSecretKeyForJWTSigningMustBeAtLeast256BitsLong!!";

    private JwtValidator newValidator(String secret) {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        return new JwtValidator(props);
    }

    private String buildToken(String secret, String subject, long expiresInMillis) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresInMillis))
                .signWith(key)
                .compact();
    }

    @Test
    void validToken_isParsedSuccessfully() {
        JwtValidator validator = newValidator(SECRET);
        String token = buildToken(SECRET, "demo", 60_000);

        Claims claims = validator.validateAndExtractClaims(token);

        assertThat(claims.getSubject()).isEqualTo("demo");
        assertThat(claims.get("role")).isEqualTo("USER");
    }

    @Test
    void expiredToken_isRejected() {
        JwtValidator validator = newValidator(SECRET);
        String token = buildToken(SECRET, "demo", -10_000); // already expired

        assertThatThrownBy(() -> validator.validateAndExtractClaims(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithWrongSecret_isRejected() {
        JwtValidator validator = newValidator(SECRET);
        String forgedToken = buildToken(
                "SomeCompletelyDifferentSecretAnAttackerMightUse123!!", "demo", 60_000);

        assertThatThrownBy(() -> validator.validateAndExtractClaims(forgedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void malformedToken_isRejected() {
        JwtValidator validator = newValidator(SECRET);

        assertThatThrownBy(() -> validator.validateAndExtractClaims("not-a-real-jwt-at-all"))
                .isInstanceOf(Exception.class);
    }
}