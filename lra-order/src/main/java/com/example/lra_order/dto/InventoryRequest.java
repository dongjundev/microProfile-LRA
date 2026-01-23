package com.example.lra_order.dto;

import java.util.List;

public record InventoryRequest(
        String orderId,
        List<OrderItem> items,
        boolean fail
) {
}
