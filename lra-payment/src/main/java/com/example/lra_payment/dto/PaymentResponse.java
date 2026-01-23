package com.example.lra_payment.dto;

public record PaymentResponse(
        String orderId,
        String status,
        String lraId
) {
}
