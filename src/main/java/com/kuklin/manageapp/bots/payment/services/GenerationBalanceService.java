package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.repositories.GenerationBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Сервис управляющий балансом пользователя
 *
 * Отвечает за:
 * - операции с балансом пользователя
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationBalanceService {

    private final GenerationBalanceRepository generationBalanceRepository;

    //Создание нового баланса, если не существует старый
    public GenerationBalance createNewBalanceIfNotExist(Long telegramId) {
        //Поиск существующего баланса
        Optional<GenerationBalance> optGenerationBalance =
                generationBalanceRepository.findByTelegramId(telegramId);

        //Если существует баналанс - возврат существующего
        if (optGenerationBalance.isPresent()) {
            return optGenerationBalance.get();
        }

        return generationBalanceRepository.save(
                new GenerationBalance()
                        .setTelegramId(telegramId)
                        .setGenerationRequests(0L)
        );
    }

    //Увеличение баланса, согласно проплаченного тарифа
    public GenerationBalance increaseBalanceByPaymentOrNull(Payment payment, Long telegramId) {
        //Оплаченный тариф
        Payment.PaymentPayload payload = payment.getPayload();

        //Проверка, что тариф сущестует в системе. Пока что, тариф только один
        if (payload.equals(Payment.PaymentPayload.PACK_10)) {
            //Получение баланса пользователя
            GenerationBalance generationBalance = generationBalanceRepository
                    .findByTelegramId(telegramId).orElse(null);

            if (generationBalance == null) return null;

            //Начисление и сохранение баланса
            generationBalance = generationBalanceRepository.save(generationBalance.setGenerationRequests(
                    generationBalance.getGenerationRequests() + 10
            ));
            return generationBalance;
        }
        return null;
    }

    //Увеличение баланса, согласно платежу
    public GenerationBalance increaseBalanceByPayment(Payment payment) {
        //Получение баланса пользователя или налл
        GenerationBalance generationBalance = generationBalanceRepository
                .findByTelegramId(payment.getTelegramId()).orElse(null);
        if (generationBalance == null) {
            //TODO ERROR
        }

        //Начисление и сохранение баланса
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
