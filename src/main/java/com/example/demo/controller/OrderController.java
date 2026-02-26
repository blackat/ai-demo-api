package com.example.demo.controller;

import com.example.demo.model.Order;
import com.example.demo.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Manage customer orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping
    @Operation(
        summary = "Get all orders",
        description = "Returns all orders. Optionally filter by customerId and/or status"
    )
    public List<Order> getAll(
            @Parameter(description = "Filter by customer ID", example = "42") @RequestParam(required = false) Long customerId,
            @Parameter(description = "Filter by status: pending, shipped, delivered, cancelled") @RequestParam(required = false) String status) {

        if (customerId != null && status != null) {
            return orderService.findByCustomerIdAndStatus(customerId, status);
        } else if (customerId != null) {
            return orderService.findByCustomerId(customerId);
        } else if (status != null) {
            return orderService.findByStatus(status);
        }
        return orderService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get order by ID",
        description = "Returns a single order by its unique ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order found"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<Order> getById(
            @Parameter(description = "Order ID", required = true) @PathVariable Long id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(
        summary = "Create a new order",
        description = "Places a new order for a customer"
    )
    @ApiResponse(responseCode = "201", description = "Order created successfully")
    public ResponseEntity<Order> create(@RequestBody Order order) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.save(order));
    }

    @PatchMapping("/{id}/status")
    @Operation(
        summary = "Update order status",
        description = "Updates the status of an existing order. Allowed values: pending, shipped, delivered, cancelled"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<Order> updateStatus(
            @Parameter(description = "Order ID") @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return orderService.updateStatus(id, status)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Cancel/delete an order",
        description = "Deletes an order by its ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Order deleted"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Order ID") @PathVariable Long id) {
        return orderService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
