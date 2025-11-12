package com.kuklin.manageapp.common.services;

import com.kuklin.manageapp.bots.payment.services.GenerationBalanceService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.repositories.TelegramUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramUserService {
    private static final Long DEFAULT_RESPONSE_COUNT = 0L;
    private final TelegramUserRepository telegramUserRepository;
    private final GenerationBalanceService generationBalanceService;

    public TelegramUser getTelegramUserByTelegramIdAndBotIdentifierOrNull(Long telegramId, BotIdentifier botIdentifier) {
        return telegramUserRepository
                .findTelegramUserByBotIdentifierAndTelegramId(botIdentifier, telegramId)
                .orElse(null)
                ;
    }

    public TelegramUser createOrGetUserByTelegram(
            BotIdentifier botIdentifier, User telegramUser) {

        Optional<TelegramUser> optionalTelegramUser =
                telegramUserRepository.findTelegramUserByBotIdentifierAndTelegramId(
                        botIdentifier, telegramUser.getId()
                );

        if (optionalTelegramUser.isPresent()) {
            generationBalanceService.createNewBalanceIfNotExist(optionalTelegramUser.get().getTelegramId());
            return optionalTelegramUser.get();
        }
        TelegramUser tgUser = TelegramUser.convertFromTelegram(telegramUser)
                .setBotIdentifier(botIdentifier)
                .setResponseCount(DEFAULT_RESPONSE_COUNT);
        tgUser = telegramUserRepository.save(tgUser);
        generationBalanceService.createNewBalanceIfNotExist(optionalTelegramUser.get().getTelegramId());

        return tgUser;
    }

    public TelegramUser save(TelegramUser telegramUser) {
        return telegramUserRepository.save(telegramUser);
    }
}
