package com.demo.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from the "jwt.*" properties in application.yml.
 * <p>
 * IMPORTANT: the "secret" value here MUST exactly match the "jwt.secret"
 * configured on the gateway-service, since the gateway validates tokens
 * signed here using that same shared secret (HMAC-SHA256). In a real
 * production system this would come from a secrets manager / vault
 * shared between services, not a literal value in two YAML files - it's
 * kept simple here for demo purposes.
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long expirationMs = 3_600_000; // 1 hour default

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
