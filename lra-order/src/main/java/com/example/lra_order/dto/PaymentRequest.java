package com.example.lra_order.dto;

import java.math.BigDecimal;

public record PaymentRequest(
        String orderId,
        BigDecimal amount,
        boolean fail
) {
}
