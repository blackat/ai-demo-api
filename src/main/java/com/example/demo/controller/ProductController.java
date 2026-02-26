package com.example.demo.controller;

import com.example.demo.model.Product;
import com.example.demo.service.ProductService;
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

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Manage the product catalog")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping
    @Operation(
        summary = "Get all products",
        description = "Returns the full list of available products in the catalog"
    )
    public List<Product> getAll() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get product by ID",
        description = "Returns a single product given its unique ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product found"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<Product> getById(
            @Parameter(description = "Product ID", required = true) @PathVariable Long id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    @Operation(
        summary = "Search products by name",
        description = "Returns products whose name contains the given keyword (case-insensitive)"
    )
    public List<Product> search(
            @Parameter(description = "Name keyword to search for", example = "laptop") @RequestParam String name) {
        return productService.search(name);
    }

    @PostMapping
    @Operation(
        summary = "Create a new product",
        description = "Adds a new product to the catalog"
    )
    @ApiResponse(responseCode = "201", description = "Product created successfully")
    public ResponseEntity<Product> create(@RequestBody Product product) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.save(product));
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update an existing product",
        description = "Updates all fields of a product identified by its ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product updated"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<Product> update(
            @Parameter(description = "Product ID") @PathVariable Long id,
            @RequestBody Product product) {
        return productService.update(id, product)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a product",
        description = "Removes a product from the catalog by its ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Product deleted"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Product ID") @PathVariable Long id) {
        return productService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
