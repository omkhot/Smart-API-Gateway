package com.sag.gateway.filter;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

/**
 * Runs first, before every other filter (including JWT auth). Ensures
 * every request - authenticated or not, successful or not - carries a
 * correlation ID that ties its log lines together, and logs a start/end
 * line with timing. If the caller already sent an X-Correlation-Id
 * header (e.g. a client that wants to trace its own request across
 * services), that value is honored instead of generating a new one.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        final String finalCorrelationId = correlationId;

        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        ServerHttpResponse originalResponse = exchange.getResponse();

        originalResponse.getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                // This part genuinely can only happen here: the resolved
                // load-balanced instance isn't known until routing has
                // already occurred, and headers can only be set before
                // the response is committed (i.e. right before writeWith).
                addUpstreamInstanceHeaderIfPresent(exchange, this);
                return super.writeWith(body);
            }
        };

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .response(decoratedResponse)
                .build();

        long startTimeMs = System.currentTimeMillis();
        log.info("--> [{}] {} {}", finalCorrelationId, request.getMethod(), request.getURI().getPath());

        return chain.filter(mutatedExchange)
                .doFinally(signalType -> {
                    long durationMs = System.currentTimeMillis() - startTimeMs;
                    HttpStatusCode status = mutatedExchange.getResponse().getStatusCode();
                    log.info("<-- [{}] {} {} -> {} ({} ms)",
                            finalCorrelationId, request.getMethod(), request.getURI().getPath(), status, durationMs);
                });
    }

    private void addUpstreamInstanceHeaderIfPresent(ServerWebExchange exchange, ServerHttpResponse response) {
        try {
            Object resolvedUrl = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            if (resolvedUrl instanceof URI uri) {
                response.getHeaders().add("X-Upstream-Instance", uri.getHost() + ":" + uri.getPort());
            }
        } catch (Exception e) {
            // Best-effort only - never let this break the actual response.
            log.debug("Could not determine upstream instance for response header: {}", e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        // Lower than the JWT filter's -200, so this runs even earlier -
        // every request gets a correlation ID and a log line, even ones
        // that get rejected by auth.
        return -300;
    }
}
