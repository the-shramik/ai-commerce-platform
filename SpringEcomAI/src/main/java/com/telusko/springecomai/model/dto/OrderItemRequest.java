package com.telusko.springecomai.model.dto;

public record OrderItemRequest(
        int productId,
        int quantity
) {}
