package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
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
    private final PricingPlanService pricingPlanService;
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

    //Увеличение баланса, согласно платежу
    public GenerationBalance increaseBalanceByPayment(Payment payment) {
        //Получение баланса пользователя или налл
        GenerationBalance generationBalance = generationBalanceRepository
                .findByTelegramId(payment.getTelegramId()).orElse(null);
        if (generationBalance == null) {
            //TODO ERROR
        }

        PricingPlan plan = pricingPlanService
                .getPricingPlanByIdOrNull(payment.getPricingPlanId());

        //Начисление и сохранение баланса
        return generationBalanceRepository.save(
                generationBalance.setGenerationRequests(
                        generationBalance.getGenerationRequests() + plan.getGenerationsCount()
                )
        );
    }

    public GenerationBalance getBalanceByTelegramIdOrNull(Long telegramId) {
        return generationBalanceRepository.findByTelegramId(telegramId).orElse(null);
    }
}
