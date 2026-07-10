package com.sag.gateway.filter;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
public class ResponseCacheGatewayFilterFactory
        extends AbstractGatewayFilterFactory<ResponseCacheGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(ResponseCacheGatewayFilterFactory.class);
    private static final String CACHE_KEY_PREFIX = "gw:cache:";
    private static final DataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

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
        GatewayFilter filter = (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            if (request.getMethod() != HttpMethod.GET) {
                return chain.filter(exchange);
            }

            String cacheKey = buildCacheKey(request);
            log.info("CACHE-DEBUG: [1] checking key '{}'", cacheKey);

            return redisTemplate.opsForValue().get(cacheKey)
                    .doOnNext(v -> log.info("CACHE-DEBUG: [2a] found cached value for '{}' -> HIT", cacheKey))
                    .flatMap(cachedBody -> serveFromCache(exchange, cachedBody))
                    .switchIfEmpty(Mono.defer(() -> {
                        log.info("CACHE-DEBUG: [2b] no cached value for '{}' -> MISS, proceeding to upstream", cacheKey);
                        return fetchAndCache(exchange, chain, cacheKey, config.getTtlSeconds());
                    }))
                    .doOnError(e -> log.error("CACHE-DEBUG: [ERR] top-level pipeline error for '{}': {}",
                            cacheKey, e.toString(), e));
        };

        // FIX: Force this filter to execute BEFORE NettyWriteResponseFilter (which is -1)
        return new OrderedGatewayFilter(filter, -2);
    }

    private Mono<Void> serveFromCache(ServerWebExchange exchange, String cachedBody) {
        log.info("CACHE-DEBUG: [3] writing HIT response back to client, bodyLength={}", cachedBody.length());
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().set("Content-Type", "application/json");
        response.getHeaders().set("X-Cache", "HIT");
        meterRegistry.counter("gateway.cache.requests", "result", "hit").increment();

        DataBuffer buffer = BUFFER_FACTORY.wrap(cachedBody.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> fetchAndCache(ServerWebExchange exchange,
                                     GatewayFilterChain chain,
                                     String cacheKey, int ttlSeconds) {

        ServerHttpResponse originalResponse = exchange.getResponse();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                log.info("CACHE-DEBUG: [4] writeWith() ENTERED for key '{}'", cacheKey);

                return DataBufferUtils.join(Flux.from(body))
                        .doOnNext(joined -> log.info("CACHE-DEBUG: [5] joined buffer has {} readable bytes",
                                joined.readableByteCount()))
                        .flatMap(joined -> {
                            byte[] bytes = new byte[joined.readableByteCount()];
                            joined.read(bytes);
                            DataBufferUtils.release(joined);

                            Mono<Void> writeToClient = super.writeWith(
                                    Mono.just(BUFFER_FACTORY.wrap(bytes)));

                            HttpStatusCode status = getStatusCode();
                            log.info("CACHE-DEBUG: [6] response status={}, bodyBytes={}",
                                    status, bytes.length);

                            if (status != null && status.is2xxSuccessful() && bytes.length > 0) {
                                String bodyAsString = new String(bytes, StandardCharsets.UTF_8);
                                log.info("CACHE-DEBUG: [7] attempting Redis SET key='{}' ttl={}s", cacheKey, ttlSeconds);

                                return redisTemplate.opsForValue()
                                        .set(cacheKey, bodyAsString, Duration.ofSeconds(ttlSeconds))
                                        .doOnSuccess(ok -> log.info("CACHE-DEBUG: [8] Redis SET SUCCESS key='{}' result={}", cacheKey, ok))
                                        .doOnError(e -> log.error("CACHE-DEBUG: [8-ERR] Redis SET FAILED key='{}': {}",
                                                cacheKey, e.toString(), e))
                                        .onErrorReturn(false)
                                        .then(writeToClient);
                            }

                            log.info("CACHE-DEBUG: [6b] NOT caching - status not 2xx or empty body");
                            return writeToClient;
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("CACHE-DEBUG: [5-EMPTY] joined body publisher was EMPTY for key '{}'", cacheKey);
                            return super.writeWith(Flux.empty());
                        }))
                        .doOnError(e -> log.error("CACHE-DEBUG: [WRITEWITH-ERR] error inside writeWith for '{}': {}",
                                cacheKey, e.toString(), e));
            }
        };

        decoratedResponse.getHeaders().set("X-Cache", "MISS");
        meterRegistry.counter("gateway.cache.requests", "result", "miss").increment();

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    private String buildCacheKey(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        return CACHE_KEY_PREFIX + request.getURI().getPath() + (query != null ? "?" + query : "");
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