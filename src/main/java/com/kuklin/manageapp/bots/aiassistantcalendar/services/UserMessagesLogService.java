package com.kuklin.manageapp.bots.aiassistantcalendar.services;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserMessagesLog;
import com.kuklin.manageapp.bots.aiassistantcalendar.repositories.UserMessagesLogRepository;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.TokenService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserMessagesLogService {

    private final UserMessagesLogRepository userMessagesLogRepository;
    private final TokenService tokenService;

    public void createLog(
            Long telegramId,
            String username,
            String firstname,
            String lastname,
            String message
    ) {

        AssistantGoogleOAuth oAuth = tokenService.getByTelegramIdOrNull(telegramId);

        String googleEmail = null;
        if (oAuth != null) {
            googleEmail = oAuth.getEmail();
        }
        userMessagesLogRepository.save(
                new UserMessagesLog()
                        .setTelegramId(telegramId)
                        .setUsername(username)
                        .setFirstname(firstname)
                        .setLastname(lastname)
                        .setGoogleEmail(googleEmail)
                        .setMessage(message)
        );
    }

    public void createLog(
            TelegramUser telegramUser,
            String message
    ) {

        AssistantGoogleOAuth oAuth = tokenService.getByTelegramIdOrNull(telegramUser.getTelegramId());

        String googleEmail = null;
        if (oAuth != null) {
            googleEmail = oAuth.getEmail();
        }
        userMessagesLogRepository.save(
                new UserMessagesLog()
                        .setTelegramId(telegramUser.getTelegramId())
                        .setUsername(telegramUser.getUsername())
                        .setFirstname(telegramUser.getFirstname())
                        .setLastname(telegramUser.getLastname())
                        .setGoogleEmail(googleEmail)
                        .setMessage(message)
        );
    }
}
