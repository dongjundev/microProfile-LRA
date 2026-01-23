package com.example.lra_payment.dto;

import java.math.BigDecimal;

public record PaymentRequest(
        String orderId,
        BigDecimal amount,
        boolean fail
) {
}
