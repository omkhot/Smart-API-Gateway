package com.sag.gateway.controller;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Demonstrates DYNAMIC ROUTE CONFIGURATION: routes can be added, changed,
 * or removed while the gateway is running, with no restart and no
 * redeploy. Under the hood this uses Spring Cloud Gateway's own
 * RouteDefinitionWriter (the same mechanism the YAML-based routes are
 * loaded through at startup) plus a RefreshRoutesEvent to tell the
 * gateway to reload its routing table immediately.
 */
@RestController
@RequestMapping("/admin/routes")
public class AdminRouteController {

    private final RouteDefinitionLocator routeDefinitionLocator;
    private final RouteDefinitionWriter routeDefinitionWriter;
    private final ApplicationEventPublisher eventPublisher;

    public AdminRouteController(RouteDefinitionLocator routeDefinitionLocator,
                                 RouteDefinitionWriter routeDefinitionWriter,
                                 ApplicationEventPublisher eventPublisher) {
        this.routeDefinitionLocator = routeDefinitionLocator;
        this.routeDefinitionWriter = routeDefinitionWriter;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping
    public Flux<RouteDefinition> listRoutes() {
        return routeDefinitionLocator.getRouteDefinitions();
    }

    @PostMapping
    public Mono<ResponseEntity<String>> addOrUpdateRoute(@RequestBody RouteDefinition definition) {
        return routeDefinitionWriter.save(Mono.just(definition))
                .then(Mono.fromRunnable(() -> eventPublisher.publishEvent(new RefreshRoutesEvent(this))))
                .thenReturn(ResponseEntity.ok(
                        "Route '" + definition.getId() + "' saved and activated - no restart needed."));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<String>> deleteRoute(@PathVariable String id) {
        return routeDefinitionWriter.delete(Mono.just(id))
                .then(Mono.fromRunnable(() -> eventPublisher.publishEvent(new RefreshRoutesEvent(this))))
                .thenReturn(ResponseEntity.ok("Route '" + id + "' deleted."))
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.status(404).body("No route found with id: " + id)));
    }
}
