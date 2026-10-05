package com.kuklin.manageapp.common.services;

import com.kuklin.manageapp.common.entities.AppUser;
import com.kuklin.manageapp.common.entities.UserAuthIdentity;
import com.kuklin.manageapp.common.entities.UserAuthIdentity.AuthProvider;
import com.kuklin.manageapp.common.repositories.UserAuthIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Способы входа пользователя (user_auth_identities).
 * Для Telegram — единое место, где telegramId сопоставляется с AppUser:
 * им пользуются и боты (TelegramUserService), и вход на сайте через Telegram Login Widget.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAuthIdentityService {

    private final UserAuthIdentityRepository identityRepository;

    public Optional<Long> findAppUserIdByTelegramId(Long telegramId) {
        return identityRepository
                .findByProviderAndProviderId(AuthProvider.TELEGRAM, telegramId.toString())
                .map(identity -> identity.getAppUser().getId());
    }

    public void linkTelegram(AppUser appUser, Long telegramId) {
        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setAppUser(appUser);
        identity.setProvider(AuthProvider.TELEGRAM);
        identity.setProviderId(telegramId.toString());
        // Telegram сам подтвердил владение аккаунтом (подпись бота / виджета)
        identity.setVerified(true);
        identityRepository.save(identity);
        log.info("Linked telegramId {} to appUserId {}", telegramId, appUser.getId());
    }
}
