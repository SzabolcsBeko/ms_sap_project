package com.techie.microservices.product.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test of the rules on ProductRequest (no Spring, no database). */
class ProductRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static ProductRequest valid() {
        return new ProductRequest(null, "iPhone 15", "Apple smartphone", "iphone_15", new BigDecimal("999.99"));
    }

    private Map<String, String> errors(ProductRequest request) {
        Set<ConstraintViolation<ProductRequest>> violations = validator.validate(request);
        return violations.stream().collect(Collectors.toMap(
                v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage, (a, b) -> a));
    }

    @Test
    void acceptsACompleteProduct() {
        assertThat(errors(valid())).isEmpty();
    }

    @Test
    void rejectsAnEmptyProduct() {
        assertThat(errors(new ProductRequest(null, null, null, null, null))).containsOnlyKeys("name", "description", "skuCode", "price");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsBlankText(String blank) {
        assertThat(errors(new ProductRequest(null, blank, blank, blank, BigDecimal.TEN)))
                .containsEntry("name", "name is required")
                .containsEntry("description", "description is required")
                .containsEntry("skuCode", "skuCode is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.01"})
    void rejectsAPriceThatIsNotPositive(String price) {
        ProductRequest request = new ProductRequest(null, "iPhone 15", "Apple smartphone", "iphone_15", new BigDecimal(price));
        assertThat(errors(request)).containsOnly(Map.entry("price", "price must be greater than 0"));
    }

    @Test
    void acceptsTheSmallestPositivePrice() {
        ProductRequest request = new ProductRequest(null, "iPhone 15", "Apple smartphone", "iphone_15", new BigDecimal("0.01"));
        assertThat(errors(request)).isEmpty();
    }
}
