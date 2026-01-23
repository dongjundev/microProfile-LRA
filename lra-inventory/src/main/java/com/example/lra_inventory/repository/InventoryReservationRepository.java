package com.example.lra_inventory.repository;

import com.example.lra_inventory.entity.InventoryReservation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    Optional<InventoryReservation> findTopByLraId(String lraId);
    Optional<InventoryReservation> findTopByOrderId(String orderId);
}
