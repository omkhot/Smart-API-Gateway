package com.sag.gateway.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResponseCacheGatewayFilterFactoryTest {

    private ReactiveValueOperations<String, String> valueOps;
    private ResponseCacheGatewayFilterFactory factory;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        ReactiveRedisTemplate<String, String> redisTemplate = mock(ReactiveRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        factory = new ResponseCacheGatewayFilterFactory(redisTemplate, meterRegistry);
    }

    @Test
    void cacheHit_servesFromRedis_andNeverCallsChain() {
        when(valueOps.get("gw:cache:/products")).thenReturn(Mono.just("{\"cached\":true}"));

        GatewayFilter filter = factory.apply(newConfig(30));

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/products").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Cache")).isEqualTo("HIT");
        verify(chain, never()).filter(any());
        verify(valueOps, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void cacheMiss_fallsThroughToChain_andMarksResponseAsMiss() {
        when(valueOps.get("gw:cache:/products")).thenReturn(Mono.empty());

        GatewayFilter filter = factory.apply(newConfig(30));

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/products").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain, times(1)).filter(captor.capture());
        assertThat(captor.getValue().getResponse().getHeaders().getFirst("X-Cache")).isEqualTo("MISS");
    }

    @Test
    void nonGetRequest_bypassesCacheEntirely_evenOnCacheableRoute() {
        GatewayFilter filter = factory.apply(newConfig(30));

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/products").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any());
        verifyNoInteractions(valueOps);
    }

    private ResponseCacheGatewayFilterFactory.Config newConfig(int ttlSeconds) {
        ResponseCacheGatewayFilterFactory.Config config = new ResponseCacheGatewayFilterFactory.Config();
        config.setTtlSeconds(ttlSeconds);
        return config;
    }
}