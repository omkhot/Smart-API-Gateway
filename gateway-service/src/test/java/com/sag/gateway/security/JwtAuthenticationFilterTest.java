package com.sag.gateway.security;

import com.sag.gateway.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtValidator jwtValidator;
    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        jwtValidator = mock(JwtValidator.class);

        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setPublicPaths(List.of("/api/auth/**", "/actuator/**"));

        filter = new JwtAuthenticationFilter(jwtValidator, securityProperties);

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void publicPath_bypassesAuthCompletely() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build());

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any());
        verifyNoInteractions(jwtValidator);
    }

    @Test
    void missingAuthorizationHeader_returns401AndNeverReachesChain() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void headerWithoutBearerPrefix_returns401() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products")
                        .header("Authorization", "Token abc123")
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void invalidOrExpiredToken_returns401() {
        when(jwtValidator.validateAndExtractClaims("bad-token"))
                .thenThrow(new JwtException("signature does not match"));

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products")
                        .header("Authorization", "Bearer bad-token")
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void validToken_forwardsRequestWithAuthHeadersPopulated() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("demo");
        when(claims.get("role")).thenReturn("USER");
        when(jwtValidator.validateAndExtractClaims("good-token")).thenReturn(claims);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products")
                        .header("Authorization", "Bearer good-token")
                        .build());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain, times(1)).filter(captor.capture());

        ServerWebExchange forwarded = captor.getValue();
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-Auth-Subject")).isEqualTo("demo");
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-Auth-Role")).isEqualTo("USER");
    }
}