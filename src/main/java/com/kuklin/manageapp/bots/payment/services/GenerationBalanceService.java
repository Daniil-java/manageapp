package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import com.kuklin.manageapp.bots.payment.repositories.GenerationBalanceRepository;
import com.kuklin.manageapp.bots.payment.services.exceptions.generationbalance.GenerationBalanceNotFoundException;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Сервис управляющий балансом генераций пользователя
 * <p>
 * Отвечает за:
 * - создание баланса генераций для пользователя
 * - получения баланса пользователя
 * - сохранение баланса
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationBalanceService {
    private final PricingPlanService pricingPlanService;
    private final GenerationBalanceRepository generationBalanceRepository;

    //Создание нового баланса, если не существует старый
    public GenerationBalance createNewBalanceIfNotExist(
            Long telegramId, BotIdentifier botIdentifier
    ) {
        //Поиск существующего баланса
        Optional<GenerationBalance> optGenerationBalance =
                generationBalanceRepository.findByTelegramIdAndBotIdentifier(telegramId, botIdentifier);

        //Если существует баналанс - возврат существующего
        if (optGenerationBalance.isPresent()) {
            return optGenerationBalance.get();
        }

        return generationBalanceRepository.save(
                new GenerationBalance()
                        .setTelegramId(telegramId)
                        .setGenerationRequests(0L)
                        .setBotIdentifier(botIdentifier)
        );
    }

    public GenerationBalance getBalanceByTelegramIdAndBotIdentifier(
            Long telegramId, BotIdentifier botIdentifier)
            throws GenerationBalanceNotFoundException {

        return generationBalanceRepository
                .findByTelegramIdAndBotIdentifier(telegramId, botIdentifier)
                .orElseThrow(() -> new GenerationBalanceNotFoundException());
    }

    public GenerationBalance save(GenerationBalance balance) {
        return generationBalanceRepository.save(balance);
    }
}
