package com.sag.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * These endpoints are never called by a client directly - they're wired
 * up as the "fallbackUri" of each route's CircuitBreaker filter (see
 * application.yml). When a downstream service is down, too slow, or the
 * breaker has already tripped open, the gateway forwards here instead of
 * waiting on (or failing against) the real service - the caller gets a
 * clean, fast, predictable response instead of a hanging request or a
 * raw connection-refused error.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> userServiceFallback() {
        return fallbackResponse("user-service");
    }

    @GetMapping("/products")
    public ResponseEntity<Map<String, Object>> productServiceFallback() {
        return fallbackResponse("product-service");
    }

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> orderServiceFallback() {
        return fallbackResponse("order-service");
    }

    private ResponseEntity<Map<String, Object>> fallbackResponse(String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 503,
                "error", "Service Unavailable",
                "service", service,
                "message", service + " is currently unavailable or responding too slowly. "
                        + "The gateway's circuit breaker is protecting the system instead of "
                        + "letting this request hang or cascade. Please try again shortly."
        ));
    }
}
