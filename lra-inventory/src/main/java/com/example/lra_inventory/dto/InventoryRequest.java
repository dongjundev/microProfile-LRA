package com.example.lra_inventory.dto;

import java.util.List;

public record InventoryRequest(
        String orderId,
        List<InventoryItem> items,
        boolean fail
) {
}
