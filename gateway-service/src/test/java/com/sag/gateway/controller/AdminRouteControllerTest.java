package com.sag.gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminRouteControllerTest {

    @Test
    void addRoute_savesDefinition_andPublishesRefreshEvent() {
        RouteDefinitionLocator locator = mock(RouteDefinitionLocator.class);
        RouteDefinitionWriter writer = mock(RouteDefinitionWriter.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(writer.save(any())).thenReturn(Mono.empty());

        AdminRouteController controller = new AdminRouteController(locator, writer, publisher);

        RouteDefinition def = new RouteDefinition();
        def.setId("demo-dynamic-route");

        ResponseEntity<String> response = controller.addOrUpdateRoute(def).block();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("demo-dynamic-route");
        verify(writer, times(1)).save(any());
        verify(publisher, times(1)).publishEvent(any());
    }

    @Test
    void deleteRoute_removesDefinition_andPublishesRefreshEvent() {
        RouteDefinitionLocator locator = mock(RouteDefinitionLocator.class);
        RouteDefinitionWriter writer = mock(RouteDefinitionWriter.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(writer.delete(any())).thenReturn(Mono.empty());

        AdminRouteController controller = new AdminRouteController(locator, writer, publisher);

        ResponseEntity<String> response = controller.deleteRoute("demo-dynamic-route").block();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(publisher, times(1)).publishEvent(any());
    }

    @Test
    void deleteRoute_returns404_whenRouteIdDoesNotExist() {
        RouteDefinitionLocator locator = mock(RouteDefinitionLocator.class);
        RouteDefinitionWriter writer = mock(RouteDefinitionWriter.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(writer.delete(any())).thenReturn(Mono.error(new IllegalArgumentException("not found")));

        AdminRouteController controller = new AdminRouteController(locator, writer, publisher);

        ResponseEntity<String> response = controller.deleteRoute("nonexistent-route").block();

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void listRoutes_returnsAllCurrentDefinitions() {
        RouteDefinitionLocator locator = mock(RouteDefinitionLocator.class);
        RouteDefinitionWriter writer = mock(RouteDefinitionWriter.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        RouteDefinition userRoute = new RouteDefinition();
        userRoute.setId("user-service-read-route");
        RouteDefinition productRoute = new RouteDefinition();
        productRoute.setId("product-service-read-route");

        when(locator.getRouteDefinitions()).thenReturn(Flux.fromIterable(List.of(userRoute, productRoute)));

        AdminRouteController controller = new AdminRouteController(locator, writer, publisher);

        List<RouteDefinition> routes = controller.listRoutes().collectList().block();

        assertThat(routes).hasSize(2);
        assertThat(routes).extracting(RouteDefinition::getId)
                .containsExactlyInAnyOrder("user-service-read-route", "product-service-read-route");
    }
}