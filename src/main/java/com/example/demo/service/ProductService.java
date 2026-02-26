package com.example.demo.service;

import com.example.demo.model.Product;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final Map<Long, Product> store = new HashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    public ProductService() {
        // Seed data
        save(new Product(null, "Laptop", "High performance laptop", 999.99, 50));
        save(new Product(null, "Mouse", "Wireless ergonomic mouse", 49.99, 200));
        save(new Product(null, "Keyboard", "Mechanical keyboard", 129.99, 150));
        save(new Product(null, "Monitor", "27-inch 4K monitor", 599.99, 30));
        save(new Product(null, "Headphones", "Noise cancelling headphones", 299.99, 75));
    }

    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<Product> search(String name) {
        return store.values().stream()
                .filter(p -> p.getName().toLowerCase().contains(name.toLowerCase()))
                .collect(Collectors.toList());
    }

    public Product save(Product product) {
        if (product.getId() == null) {
            product.setId(idGen.getAndIncrement());
        }
        store.put(product.getId(), product);
        return product;
    }

    public Optional<Product> update(Long id, Product product) {
        if (!store.containsKey(id)) return Optional.empty();
        product.setId(id);
        store.put(id, product);
        return Optional.of(product);
    }

    public boolean delete(Long id) {
        return store.remove(id) != null;
    }
}
