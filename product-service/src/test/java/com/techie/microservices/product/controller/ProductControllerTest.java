package com.techie.microservices.product.controller;

import com.techie.microservices.product.dto.ProductRequest;
import com.techie.microservices.product.dto.ProductResponse;
import com.techie.microservices.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web-layer test: request validation and error format, with the service mocked (no database). */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void createsAValidProduct() throws Exception {
        when(productService.createProduct(any())).thenReturn(
                new ProductResponse("1", "iPhone 15", "Apple smartphone", "iphone_15", new BigDecimal("999.99")));

        mockMvc.perform(post("/api/product").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "iPhone 15", "description": "Apple smartphone", "skuCode": "iphone_15", "price": 999.99}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void rejectsAnEmptyProductAndNamesEveryMissingField() throws Exception {
        mockMvc.perform(post("/api/product").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid product"))
                .andExpect(jsonPath("$.errors.name").value("name is required"))
                .andExpect(jsonPath("$.errors.description").value("description is required"))
                .andExpect(jsonPath("$.errors.skuCode").value("skuCode is required"))
                .andExpect(jsonPath("$.errors.price").value("price is required"));

        verify(productService, never()).createProduct(any(ProductRequest.class));
    }

    @Test
    void rejectsAPriceOfZero() throws Exception {
        mockMvc.perform(post("/api/product").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "iPhone 15", "description": "Apple smartphone", "skuCode": "iphone_15", "price": 0}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.price").value("price must be greater than 0"))
                .andExpect(jsonPath("$.errors.name").doesNotExist());

        verify(productService, never()).createProduct(any(ProductRequest.class));
    }

    @Test
    void stillAnswers400ForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/product").contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest());
    }
}
