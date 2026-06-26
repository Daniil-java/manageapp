package com.kuklin.manageapp.common.services;

import com.kuklin.manageapp.common.entities.AppUser;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppUserService {
    private final AppUserRepository repository;

    public AppUser getAppUserByIdOrNull(Long userId) {
        return repository.findById(userId).orElse(null);
    }

    public AppUser createUser(AppUser user) {
        return repository.save(user);
    }

    public AppUser createUserByTelegram(User tgUser) {
        return createUser(
                new AppUser()
                        .setUsername(tgUser.getUserName())
                        .setFirstname(tgUser.getFirstName())
                        .setLastname(tgUser.getLastName())
        );
    }

    public AppUser createUserByTelegram(TelegramUser tgUser) {
        return createUser(
                new AppUser()
                        .setUsername(tgUser.getUsername())
                        .setFirstname(tgUser.getFirstname())
                        .setLastname(tgUser.getLastname())
        );
    }
}
