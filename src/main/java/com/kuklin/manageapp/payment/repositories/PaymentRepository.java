package com.kuklin.manageapp.payment.repositories;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.models.common.Currency;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    /** Боты, у которых есть платежи в этой валюте и статусе, — кому сверять звёзды. */
    @Query("SELECT DISTINCT p.botIdentifier FROM Payment p WHERE p.currency = :currency AND p.status = :status")
    List<BotIdentifier> findBotIdentifiersByCurrencyAndStatus(@Param("currency") Currency currency,
                                                              @Param("status") Payment.PaymentStatus status);
}
