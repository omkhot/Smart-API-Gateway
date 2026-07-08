package com.sag.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Bound from "gateway.security.public-paths" in application.yml.
 * Any incoming request whose path matches one of these Ant-style
 * patterns skips JWT validation entirely (e.g. login/register, actuator
 * health checks, the dynamic-routing admin API, and circuit-breaker
 * fallback endpoints).
 */
@ConfigurationProperties(prefix = "gateway.security")
public class SecurityProperties {

    private List<String> publicPaths = List.of();

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }
}
