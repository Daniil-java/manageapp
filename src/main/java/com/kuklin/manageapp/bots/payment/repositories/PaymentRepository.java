package com.kuklin.manageapp.bots.payment.repositories;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTelegramInvoicePayload(String telegramPayload);
    Optional<Payment> findByTelegramInvoicePayloadAndStatus(String telegramPayload, Payment.PaymentStatus status);
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);
    Optional<Payment> findByProviderPaymentIdAndTelegramInvoicePayload(String providerPaymentId, String telegramPayload);
}
