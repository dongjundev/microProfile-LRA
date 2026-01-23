package com.example.lra_order.dto;

public record PaymentResponse(String orderId, String status, String lraId) {
}
