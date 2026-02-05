package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.repositories.GenerationBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationBalanceService {

    private final GenerationBalanceRepository generationBalanceRepository;

    public GenerationBalance createNewBalanceIfNotExist(Long telegramId) {
        Optional<GenerationBalance> optGenerationBalance =
                generationBalanceRepository.findByTelegramId(telegramId);

        if (optGenerationBalance.isPresent()) {
            return optGenerationBalance.get();
        }

        return generationBalanceRepository.save(
                new GenerationBalance()
                        .setTelegramId(telegramId)
                        .setGenerationRequests(0L)
        );
    }

    public GenerationBalance increaseBalanceByPaymentOrNull(Payment payment, Long telegramId) {
        Payment.PaymentPayload payload = payment.getPayload();

        if (payload.equals(Payment.PaymentPayload.PACK_10)) {
            GenerationBalance generationBalance = generationBalanceRepository
                    .findByTelegramId(telegramId).orElse(null);

            if (generationBalance == null) return null;

            generationBalance = generationBalanceRepository.save(generationBalance.setGenerationRequests(
                    generationBalance.getGenerationRequests() + 10
            ));
            return generationBalance;
        }
        return null;
    }

    public GenerationBalance increaseBalanceByPayment(Payment payment) {
        GenerationBalance generationBalance = generationBalanceRepository
                .findByTelegramId(payment.getTelegramId()).orElse(null);
        if (generationBalance == null) {
            //TODO ERROR
        }

        return generationBalanceRepository.save(
                generationBalance.setGenerationRequests(
                        generationBalance.getGenerationRequests() + payment.getPayload().getGenerationsCount()
                )
        );
    }

    public GenerationBalance getBalanceByTelegramIdOrNull(Long telegramId) {
        return generationBalanceRepository.findByTelegramId(telegramId).orElse(null);
    }
}
