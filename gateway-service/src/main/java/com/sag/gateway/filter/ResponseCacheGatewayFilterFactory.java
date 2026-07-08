package com.sag.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Named filter "ResponseCache" - usable per-route in application.yml as:
 * <pre>
 *   filters:
 *     - name: ResponseCache
 *       args:
 *         ttlSeconds: 30
 * </pre>
 * Only GET requests are cached (write operations are never cached).
 * <p>
 * On a cache MISS: the request proceeds to the real downstream service,
 * and if the response is 2xx, its body is stored in Redis under a key
 * derived from the request path + query string, with the given TTL.
 * On a cache HIT: the stored response is written back immediately -
 * the downstream service is never called at all.
 * <p>
 * Either way, the response carries an "X-Cache: HIT" or "X-Cache: MISS"
 * header so it's obvious which happened when testing.
 */
@Component
public class ResponseCacheGatewayFilterFactory
        extends AbstractGatewayFilterFactory<ResponseCacheGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(ResponseCacheGatewayFilterFactory.class);
    private static final String CACHE_KEY_PREFIX = "gw:cache:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResponseCacheGatewayFilterFactory(ReactiveRedisTemplate<String, String> redisTemplate,
                                              MeterRegistry meterRegistry) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("ttlSeconds");
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            if (request.getMethod() != HttpMethod.GET) {
                return chain.filter(exchange);
            }

            String cacheKey = buildCacheKey(request);

            return redisTemplate.opsForValue().get(cacheKey)
                    .flatMap(json -> writeFromCache(exchange, chain, cacheKey, config.getTtlSeconds(), json))
                    .switchIfEmpty(Mono.defer(() -> proceedAndCache(exchange, chain, cacheKey, config.getTtlSeconds())));
        };
    }

    private String buildCacheKey(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        return CACHE_KEY_PREFIX + request.getURI().getPath() + (query != null ? "?" + query : "");
    }

    private Mono<Void> writeFromCache(ServerWebExchange exchange, GatewayFilterChain chain,
                                       String cacheKey, int ttlSeconds, String json) {
        try {
            CachedResponse cached = objectMapper.readValue(json, CachedResponse.class);
            byte[] bodyBytes = Base64.getDecoder().decode(cached.bodyBase64());

            // Only mutate the response once we know the cached entry is
            // fully valid and decodable - avoids leaving a half-mutated
            // response behind if something about the cached data is corrupt.
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.valueOf(cached.status()));
            response.getHeaders().add("Content-Type", cached.contentType());
            response.getHeaders().add("X-Cache", "HIT");
            meterRegistry.counter("gateway.cache.requests", "result", "hit").increment();

            DataBuffer buffer = response.bufferFactory().wrap(bodyBytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.warn("Could not read cached entry for key '{}', treating as cache miss: {}", cacheKey, e.getMessage());
            return proceedAndCache(exchange, chain, cacheKey, ttlSeconds);
        }
    }

    private Mono<Void> proceedAndCache(ServerWebExchange exchange, GatewayFilterChain chain,
                                        String cacheKey, int ttlSeconds) {
        ServerHttpResponse originalResponse = exchange.getResponse();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                return DataBufferUtils.join(body).flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    if (originalResponse.getStatusCode() != null
                            && originalResponse.getStatusCode().is2xxSuccessful()) {
                        cacheResponseAsync(cacheKey, ttlSeconds, originalResponse, bytes);
                    }

                    DataBuffer bufferForClient = bufferFactory().wrap(bytes);
                    return super.writeWith(Mono.just(bufferForClient));
                });
            }
        };

        decoratedResponse.getHeaders().add("X-Cache", "MISS");
        meterRegistry.counter("gateway.cache.requests", "result", "miss").increment();

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    private void cacheResponseAsync(String cacheKey, int ttlSeconds, ServerHttpResponse response, byte[] bytes) {
        try {
            String contentType = response.getHeaders().getContentType() != null
                    ? response.getHeaders().getContentType().toString()
                    : MediaType.APPLICATION_JSON_VALUE;

            CachedResponse cached = new CachedResponse(
                    response.getStatusCode().value(),
                    contentType,
                    Base64.getEncoder().encodeToString(bytes)
            );
            String json = objectMapper.writeValueAsString(cached);

            // Fire-and-forget: caching is best-effort and must never slow
            // down or break the actual response being sent to the client.
            redisTemplate.opsForValue()
                    .set(cacheKey, json, Duration.ofSeconds(ttlSeconds))
                    .subscribe(
                            success -> log.debug("Cached response for key '{}' (ttl {}s)", cacheKey, ttlSeconds),
                            error -> log.warn("Failed to cache response for key '{}': {}", cacheKey, error.getMessage())
                    );
        } catch (Exception e) {
            log.warn("Failed to serialize response for caching, key '{}': {}", cacheKey, e.getMessage());
        }
    }

    /**
     * What actually gets stored in Redis as JSON, per cache key.
     */
    private record CachedResponse(int status, String contentType, String bodyBase64) {
    }

    public static class Config {
        private int ttlSeconds = 30;

        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }
}
