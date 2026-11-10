package com.techie.microservices.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ProductRequest(
        String id,
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "description is required") String description,
        @NotBlank(message = "skuCode is required") String skuCode,
        @NotNull(message = "price is required") @Positive(message = "price must be greater than 0") BigDecimal price) {
}
