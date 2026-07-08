package com.demo.user.controller;

import com.demo.user.model.User;
import com.demo.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * NOTE ON PATH: this controller's base path is "/users" (not "/api/users").
 * The gateway strips the "/api" prefix before forwarding, so a client
 * hitting  GET http://localhost:8080/api/users/1  (through the gateway)
 * arrives here as  GET /users/1.
 * <p>
 * This service can also be called directly on its own port (8081) for
 * local debugging, bypassing the gateway entirely.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @Value("${server.port}")
    private String port;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping
    public ResponseEntity<User> createUser(@Valid @RequestBody User user) {
        User created = userService.createUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @Valid @RequestBody User user) {
        return ResponseEntity.ok(userService.updateUser(id, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns which physical instance (port) served the request.
     * Not needed for CRUD, but very useful later when you run multiple
     * instances of this service behind the gateway's load balancer and
     * want to visibly prove requests are being distributed across them.
     */
    @GetMapping("/meta/instance-info")
    public Map<String, String> instanceInfo() {
        return Map.of("service", "user-service", "port", port);
    }
}
