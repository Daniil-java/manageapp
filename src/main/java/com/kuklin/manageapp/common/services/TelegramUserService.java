package com.kuklin.manageapp.common.services;

import com.kuklin.manageapp.payment.services.GenerationBalanceService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.repositories.TelegramUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.List;
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

    public List<TelegramUser> getAllTelegramUsersByBotIdentifierOrNull(BotIdentifier botIdentifier) {
        return telegramUserRepository.findAllByBotIdentifier(botIdentifier);
    }

    @Transactional
    public TelegramUser createOrGetUserByTelegram(
            BotIdentifier botIdentifier, User telegramUser) {

        Optional<TelegramUser> optionalTelegramUser =
                telegramUserRepository.findTelegramUserByBotIdentifierAndTelegramId(
                        botIdentifier, telegramUser.getId()
                );

        //Если пользователь существует - возвращаем
        if (optionalTelegramUser.isPresent()) {
            //Создаем баланс генераций для нового пользователя
            generationBalanceService.createNewBalanceIfNotExist(
                    optionalTelegramUser.get().getTelegramId(),
                    botIdentifier
            );
            //Если пользователь блокировал бота - активируем
            TelegramUser tgUser = optionalTelegramUser.get();
            if (tgUser.getIsBotBlocked()) {
                tgUser = telegramUserRepository.save(tgUser.setIsBotBlocked(false));
            }
            return tgUser;
        }
        TelegramUser tgUser = TelegramUser.convertFromTelegram(telegramUser)
                .setBotIdentifier(botIdentifier)
                .setResponseCount(DEFAULT_RESPONSE_COUNT);
        tgUser = telegramUserRepository.save(tgUser);
        generationBalanceService.createNewBalanceIfNotExist(
                tgUser.getTelegramId(),
                botIdentifier
        );

        return tgUser;
    }

    public void deactivateUser(Long telegramId, BotIdentifier botIdentifier) {
        TelegramUser telegramUser =
                getTelegramUserByTelegramIdAndBotIdentifierOrNull(telegramId, botIdentifier);

        if (telegramUser != null) {
            if (!telegramUser.getIsBotBlocked()) {
                telegramUser.setIsBotBlocked(true);
                telegramUserRepository.save(telegramUser);
            }
        } else {
            log.error("User with tgId: {}, and botId: {} don't exists!", telegramId, botIdentifier);
        }
    }

    public TelegramUser activateUserOrNull(Long telegramId, BotIdentifier botIdentifier) {
        TelegramUser telegramUser =
                getTelegramUserByTelegramIdAndBotIdentifierOrNull(telegramId, botIdentifier);

        if (telegramUser != null) {
            if (telegramUser.getIsBotBlocked()) {
                telegramUser.setIsBotBlocked(false);
                return telegramUserRepository.save(telegramUser);
            } else {
                return telegramUser;
            }
        } else {
            log.error("User with tgId: {}, and botId: {} don't exists!", telegramId, botIdentifier);
            return null;
        }
    }

    public TelegramUser save(TelegramUser telegramUser) {
        return telegramUserRepository.save(telegramUser);
    }

    public List<TelegramUser> getActiveUsersByBot(BotIdentifier botIdentifier) {
        return telegramUserRepository.findAllByBotIdentifierAndIsBotBlockedFalse(botIdentifier);
    }
}
