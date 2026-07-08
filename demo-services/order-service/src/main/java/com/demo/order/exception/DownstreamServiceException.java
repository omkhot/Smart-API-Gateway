package com.demo.order.exception;

/**
 * Thrown when user-service or product-service can't fulfil a request
 * (not found, unreachable, business-rule conflict like insufficient stock).
 * Kept as a single exception type for now to keep the demo simple; this
 * is exactly the seam where a circuit breaker will be added in the next
 * phase of the gateway project.
 */
public class DownstreamServiceException extends RuntimeException {
    public DownstreamServiceException(String message) {
        super(message);
    }
}
