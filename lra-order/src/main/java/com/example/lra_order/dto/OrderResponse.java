package com.example.lra_order.dto;

public record OrderResponse(
        String orderId,
        String status,
        String lraId,
        String inventoryStatus,
        String paymentStatus
) {
}
