package com.sag.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from "jwt.*" in application.yml.
 * <p>
 * The gateway only ever VALIDATES a token's signature and expiry using
 * this secret - it never issues tokens itself (see auth-service, the
 * only place that does). The "secret" value here MUST be identical to
 * the one configured in demo-services/auth-service/application.yml.
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
