package com.kuklin.manageapp.bots.aiassistantcalendar.services;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserAuthNotification;
import com.kuklin.manageapp.bots.aiassistantcalendar.repositories.UserAuthNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAuthNotificationService {
    private final UserAuthNotificationRepository authNotificationRepository;

    public List<UserAuthNotification> getPendingUserAuthNotificationAndExpired() {
        return authNotificationRepository.findByStatusAndExecuteAtLessThanEqualOrderByExecuteAtAsc(
                UserAuthNotification.Status.PENDING,
                LocalDateTime.now()
        );
    }

    public UserAuthNotification save(UserAuthNotification userAuthNotification) {
        return authNotificationRepository.save(userAuthNotification);
    }

    public UserAuthNotification create(Long telegramId, LocalDateTime localDateTime, String text) {
        return authNotificationRepository.save(
                new UserAuthNotification()
                        .setTelegramId(telegramId)
                        .setStatus(UserAuthNotification.Status.PENDING)
                        .setExecuteAt(localDateTime)
                        .setText(text)
        );
    }
}
