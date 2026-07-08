package com.demo.order.dto;

/**
 * Minimal mirror of user-service's User resource. Kept as a separate DTO
 * (rather than sharing the entity class across services) on purpose:
 * each microservice should own its data contract independently.
 */
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private String city;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
