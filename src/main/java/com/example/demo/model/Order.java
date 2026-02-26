package com.example.demo.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Order entity")
public class Order {

    @Schema(description = "Unique order ID", example = "101")
    private Long id;

    @Schema(description = "Customer ID", example = "42")
    private Long customerId;

    @Schema(description = "Product ID", example = "1")
    private Long productId;

    @Schema(description = "Quantity ordered", example = "3")
    private Integer quantity;

    @Schema(description = "Order status", example = "pending", allowableValues = {"pending", "shipped", "delivered", "cancelled"})
    private String status;

    @Schema(description = "Total price in USD", example = "2999.97")
    private Double totalPrice;

    public Order() {}

    public Order(Long id, Long customerId, Long productId, Integer quantity, String status, Double totalPrice) {
        this.id = id;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
        this.totalPrice = totalPrice;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(Double totalPrice) { this.totalPrice = totalPrice; }
}
