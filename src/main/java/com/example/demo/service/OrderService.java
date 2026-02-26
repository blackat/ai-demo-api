package com.example.demo.service;

import com.example.demo.model.Order;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final Map<Long, Order> store = new HashMap<>();
    private final AtomicLong idGen = new AtomicLong(101);

    public OrderService() {
        // Seed data
        save(new Order(null, 42L, 1L, 2, "pending", 1999.98));
        save(new Order(null, 42L, 2L, 1, "shipped", 49.99));
        save(new Order(null, 55L, 3L, 3, "delivered", 389.97));
        save(new Order(null, 55L, 4L, 1, "pending", 599.99));
        save(new Order(null, 77L, 1L, 1, "cancelled", 999.99));
    }

    public List<Order> findAll() {
        return new ArrayList<>(store.values());
    }

    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<Order> findByCustomerId(Long customerId) {
        return store.values().stream()
                .filter(o -> o.getCustomerId().equals(customerId))
                .collect(Collectors.toList());
    }

    public List<Order> findByStatus(String status) {
        return store.values().stream()
                .filter(o -> o.getStatus().equalsIgnoreCase(status))
                .collect(Collectors.toList());
    }

    public List<Order> findByCustomerIdAndStatus(Long customerId, String status) {
        return store.values().stream()
                .filter(o -> o.getCustomerId().equals(customerId) && o.getStatus().equalsIgnoreCase(status))
                .collect(Collectors.toList());
    }

    public Order save(Order order) {
        if (order.getId() == null) {
            order.setId(idGen.getAndIncrement());
        }
        store.put(order.getId(), order);
        return order;
    }

    public Optional<Order> updateStatus(Long id, String status) {
        Order order = store.get(id);
        if (order == null) return Optional.empty();
        order.setStatus(status);
        return Optional.of(order);
    }

    public boolean delete(Long id) {
        return store.remove(id) != null;
    }
}
