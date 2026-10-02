package com.kuklin.manageapp.payment.services;

import com.kuklin.manageapp.payment.entities.GenerationBalance;
import com.kuklin.manageapp.payment.repositories.GenerationBalanceRepository;
import com.kuklin.manageapp.payment.services.exceptions.generationbalance.GenerationBalanceNotFoundException;
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
    private final GenerationBalanceRepository generationBalanceRepository;

    //Создание нового баланса, если не существует старый
    public GenerationBalance createNewBalanceIfNotExist(
            Long appUserId, BotIdentifier botIdentifier
    ) {
        //Поиск существующего баланса
        Optional<GenerationBalance> optGenerationBalance =
                generationBalanceRepository.findByAppUserIdAndBotIdentifier(appUserId, botIdentifier);

        //Если существует баналанс - возврат существующего
        if (optGenerationBalance.isPresent()) {
            return optGenerationBalance.get();
        }

        return generationBalanceRepository.save(
                new GenerationBalance()
                        .setAppUserId(appUserId)
                        .setGenerationRequests(0L)
                        .setBotIdentifier(botIdentifier)
        );
    }

    public GenerationBalance getBalanceByAppUserIdAndBotIdentifier(
            Long appUserId, BotIdentifier botIdentifier)
            throws GenerationBalanceNotFoundException {

        return generationBalanceRepository
                .findByAppUserIdAndBotIdentifier(appUserId, botIdentifier)
                .orElseThrow(GenerationBalanceNotFoundException::new);
    }

    public GenerationBalance save(GenerationBalance balance) {
        return generationBalanceRepository.save(balance);
    }
}
