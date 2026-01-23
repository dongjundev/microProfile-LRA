package com.example.lra_payment.repository;

import com.example.lra_payment.entity.PaymentAuthorization;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAuthorizationRepository extends JpaRepository<PaymentAuthorization, Long> {
    Optional<PaymentAuthorization> findTopByLraId(String lraId);
    Optional<PaymentAuthorization> findTopByOrderId(String orderId);
}
