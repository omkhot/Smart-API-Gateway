package com.demo.order.client;

import com.demo.order.dto.ProductDto;
import com.demo.order.exception.DownstreamServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ProductClient {

    private final RestTemplate restTemplate;
    private final String productServiceBaseUrl;

    public ProductClient(RestTemplate restTemplate,
                          @Value("${services.product-service.base-url}") String productServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.productServiceBaseUrl = productServiceBaseUrl;
    }

    public ProductDto getProductById(Long productId) {
        try {
            return restTemplate.getForObject(productServiceBaseUrl + "/products/" + productId, ProductDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new DownstreamServiceException("Product not found with id: " + productId);
        } catch (ResourceAccessException ex) {
            throw new DownstreamServiceException("product-service is unreachable. Is it running on " + productServiceBaseUrl + " ?");
        }
    }

    public ProductDto reduceStock(Long productId, int quantity) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(productServiceBaseUrl + "/products/" + productId + "/reduce-stock")
                    .queryParam("quantity", quantity)
                    .toUriString();

            return restTemplate.exchange(url, HttpMethod.PATCH, HttpEntity.EMPTY, ProductDto.class).getBody();
        } catch (HttpClientErrorException.Conflict ex) {
            throw new DownstreamServiceException("Insufficient stock for product id: " + productId);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new DownstreamServiceException("Product not found with id: " + productId);
        } catch (ResourceAccessException ex) {
            throw new DownstreamServiceException("product-service is unreachable. Is it running on " + productServiceBaseUrl + " ?");
        }
    }
}
