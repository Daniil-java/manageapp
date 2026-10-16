package com.kuklin.manageapp.payment.repositories;

import com.kuklin.manageapp.payment.entities.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTelegramInvoicePayload(String telegramPayload);

    /** Счёт под блокировкой до конца транзакции — successful_payment по одному счёту обрабатываются по очереди. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.telegramInvoicePayload = :payload")
    Optional<Payment> findByTelegramInvoicePayloadForUpdate(@Param("payload") String telegramPayload);

    Optional<Payment> findByTelegramInvoicePayloadAndStatus(String telegramPayload, Payment.PaymentStatus status);
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);
    Optional<Payment> findByProviderPaymentIdAndTelegramInvoicePayload(String providerPaymentId, String telegramPayload);
}
