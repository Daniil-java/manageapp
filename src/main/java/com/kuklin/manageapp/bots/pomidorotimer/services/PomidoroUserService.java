package com.kuklin.manageapp.bots.pomidorotimer.services;


import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import com.kuklin.manageapp.bots.pomidorotimer.models.BotState;
import com.kuklin.manageapp.bots.pomidorotimer.repositories.PomidoroUserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PomidoroUserService {
    private final PomidoroUserRepository pomidoroUserRepository;

    public UserEntity updateUserEntity(UserEntity userEntity) {
        return pomidoroUserRepository.save(userEntity);
    }
    @Transactional
    public UserEntity getOrCreateUser(User userInfo, BotState botState) {
        Optional<UserEntity> userEntity = pomidoroUserRepository.findUserEntityByTelegramId(userInfo.getId());
        if (userEntity.isPresent()) {
            if (botState != null) {
                userEntity.get().setBotState(botState);
            }
            return pomidoroUserRepository.save(userEntity.get());
        } else {
            return pomidoroUserRepository.save(new UserEntity()
                    .setTelegramId(userInfo.getId())
                    .setChatId(userInfo.getId())
                    .setUsername(userInfo.getUserName())
                    .setFirstname(userInfo.getFirstName())
                    .setLastname(userInfo.getLastName())
                    .setLanguageCode(userInfo.getLanguageCode())
                    .setBotState(botState)
            );
        }
    }

    public UserEntity createUser(UserEntity userEntity) {
        return pomidoroUserRepository.save(userEntity);
    }

    public UserEntity getUserByTelegramIdOrNull(Long telegramId) {
        return pomidoroUserRepository.findUserEntityByTelegramId(telegramId)
                .orElse(null);
    }

    public UserEntity getUserByTelegramChatIdOrNull(Long telegramChatId) {
        return pomidoroUserRepository.findUserByChatId(telegramChatId)
                .orElse(null);
    }

    public UserEntity getUserByEntityIdOrNull(Long userId) {
        return pomidoroUserRepository.findById(userId)
                .orElse(null);
    }
}
