package com.demo.order.service;

import com.demo.order.client.ProductClient;
import com.demo.order.client.UserClient;
import com.demo.order.dto.PlaceOrderRequest;
import com.demo.order.dto.ProductDto;
import com.demo.order.dto.UserDto;
import com.demo.order.exception.ResourceNotFoundException;
import com.demo.order.model.Order;
import com.demo.order.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserClient userClient;
    private final ProductClient productClient;

    public OrderService(OrderRepository orderRepository, UserClient userClient, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.userClient = userClient;
        this.productClient = productClient;
    }

    /**
     * Placing an order requires talking to two other services:
     *   1. user-service   -> confirm the user exists
     *   2. product-service -> confirm the product exists, has enough stock,
     *                          and get its current price, then decrement stock
     * This is the exact seam where the gateway's circuit breaker will be
     * demonstrated in the next phase (by killing user-service or
     * product-service mid-demo and showing the fallback response).
     */
    public Order placeOrder(PlaceOrderRequest request) {
        UserDto user = userClient.getUserById(request.getUserId());
        ProductDto product = productClient.getProductById(request.getProductId());

        // Reserve stock on product-service (throws if insufficient)
        productClient.reduceStock(product.getId(), request.getQuantity());

        BigDecimal totalPrice = product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));

        Order order = new Order(user.getId(), product.getId(), request.getQuantity(), totalPrice, "PLACED");
        return orderRepository.save(order);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }
}
