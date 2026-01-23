package com.example.lra_inventory.dto;

public record InventoryResponse(
        String orderId,
        String status,
        String lraId
) {
}
