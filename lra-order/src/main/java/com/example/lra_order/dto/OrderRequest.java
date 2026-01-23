package com.example.lra_order.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderRequest(
        String orderId,
        List<OrderItem> items,
        BigDecimal amount,
        boolean failInventory,
        boolean failPayment
) {
}
