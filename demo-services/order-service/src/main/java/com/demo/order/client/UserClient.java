package com.demo.order.client;

import com.demo.order.dto.UserDto;
import com.demo.order.exception.DownstreamServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Talks DIRECTLY to user-service (e.g. http://localhost:8081), NOT through
 * the gateway. In this project's design, the gateway is the entry point
 * for external clients; internal service-to-service calls go point to
 * point, which mirrors how most real microservice systems work.
 */
@Component
public class UserClient {

    private final RestTemplate restTemplate;
    private final String userServiceBaseUrl;

    public UserClient(RestTemplate restTemplate,
                       @Value("${services.user-service.base-url}") String userServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.userServiceBaseUrl = userServiceBaseUrl;
    }

    public UserDto getUserById(Long userId) {
        try {
            return restTemplate.getForObject(userServiceBaseUrl + "/users/" + userId, UserDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new DownstreamServiceException("User not found with id: " + userId);
        } catch (ResourceAccessException ex) {
            throw new DownstreamServiceException("user-service is unreachable. Is it running on " + userServiceBaseUrl + " ?");
        }
    }
}
