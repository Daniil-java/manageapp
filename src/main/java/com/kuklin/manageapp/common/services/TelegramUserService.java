package com.kuklin.manageapp.common.services;

import com.kuklin.manageapp.common.entities.AppUser;
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
    private final AppUserService appUserService;

    public TelegramUser getTelegramUserByTelegramIdAndBotIdentifierOrNull(Long telegramId, BotIdentifier botIdentifier) {
        return telegramUserRepository
                .findTelegramUserByBotIdentifierAndTelegramId(botIdentifier, telegramId)
                .orElse(null)
                ;
    }

    public Optional<TelegramUser> findByAppUserIdAndBotIdentifier(Long appUserId, BotIdentifier botIdentifier) {
        return telegramUserRepository.findTelegramUserByAppUserIdAndBotIdentifier(appUserId, botIdentifier);
    }

    public List<TelegramUser> getAllTelegramUsersByBotIdentifierOrNull(BotIdentifier botIdentifier) {
        return telegramUserRepository.findAllByBotIdentifier(botIdentifier);
    }

    @Transactional
    public TelegramUser createOrGetUserByTelegram(BotIdentifier botIdentifier, User telegramUser) {

        Optional<TelegramUser> optionalTelegramUser =
                telegramUserRepository.findTelegramUserByBotIdentifierAndTelegramId(
                        botIdentifier, telegramUser.getId()
                );

        // СЦЕНАРИЙ 1: Пользователь уже пользовался ИМЕННО ЭТИМ ботом
        if (optionalTelegramUser.isPresent()) {
            TelegramUser tgUser = optionalTelegramUser.get();

            generationBalanceService.createNewBalanceIfNotExist(
                    tgUser.getAppUserId(), botIdentifier
            );

            if (tgUser.getIsBotBlocked()) {
                tgUser = telegramUserRepository.save(tgUser.setIsBotBlocked(false));
            }

            // Безопасно проверяем привязку к AppUser
            checkIfAppUserExist(tgUser);
            return tgUser;
        }

        // СЦЕНАРИЙ 2: Пользователь новый для этого бота.
        // Ищем, не пользовался ли он ДРУГИМИ нашими ботами.
        // Используем findFirst, чтобы избежать ошибки NonUniqueResultException
        TelegramUser userFromOtherBot = telegramUserRepository
                .findFirstByTelegramId(telegramUser.getId())
                .orElse(null);

        Long appUserId;

        if (userFromOtherBot != null && userFromOtherBot.getAppUserId() != null) {
            // Он уже есть в экосистеме, берем его глобальный ID
            appUserId = userFromOtherBot.getAppUserId();
        } else {
            // Совершенно новый человек. Создаем ему глобальный аккаунт.
            AppUser newAppUser = appUserService.createUserByTelegram(telegramUser);
            appUserId = newAppUser.getId();
        }

        // Создаем профиль конкретно для ЭТОГО бота
        TelegramUser newTgUser = TelegramUser.convertFromTelegram(telegramUser)
                .setBotIdentifier(botIdentifier)
                .setResponseCount(DEFAULT_RESPONSE_COUNT)
                .setIsBotBlocked(false)
                .setAppUserId(appUserId); // <-- Заполнили недостающее поле

        newTgUser = telegramUserRepository.save(newTgUser);

        generationBalanceService.createNewBalanceIfNotExist(
                newTgUser.getAppUserId(),
                botIdentifier
        );

        return newTgUser;
    }

    private void checkIfAppUserExist(TelegramUser telegramUser) {
        if (telegramUser.getAppUserId() == null) {
            // Подстраховка: вдруг в БД есть старая запись от другого бота,
            // у которой AppUser УЖЕ создан? Ищем её.
            TelegramUser userWithAppId = telegramUserRepository
                    .findFirstByTelegramIdAndAppUserIdIsNotNull(telegramUser.getTelegramId())
                    .orElse(null);

            if (userWithAppId != null) {
                // Переиспользуем найденный AppUser
                telegramUser.setAppUserId(userWithAppId.getAppUserId());
            } else {
                // Иначе создаем новый
                AppUser appUser = appUserService.createUserByTelegram(telegramUser);
                telegramUser.setAppUserId(appUser.getId());
            }
            telegramUserRepository.save(telegramUser);
        }
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

    public Optional<TelegramUser> findFirstByTelegramId(Long telegramId) {
        return telegramUserRepository.findFirstByTelegramId(telegramId);
    }
}
