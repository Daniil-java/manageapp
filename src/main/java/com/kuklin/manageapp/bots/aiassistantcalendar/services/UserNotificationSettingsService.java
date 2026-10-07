package com.kuklin.manageapp.bots.aiassistantcalendar.services;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserNotificationSettings;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.repositories.UserNotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserNotificationSettingsService {
    private final UserNotificationSettingsRepository repository;
    private static final LocalTime DEF_TIME = LocalTime.of(6, 0);
    private static final Integer UTC_DEF_TIME = 3;

    public UserNotificationSettings getOrCreate(Long telegramId) {
        return repository.findByTelegramId(telegramId)
                .orElseGet(() -> {
                    UserNotificationSettings settings = new UserNotificationSettings()
                            .setTelegramId(telegramId)
                            .setDailyEnabled(true)
                            .setUtcOffsetHours(UTC_DEF_TIME)
                            .setDailyTime(DEF_TIME)
                            .setLastDailyNotified(null);
                    return repository.save(settings);
                });
    }

    public UserNotificationSettings findByTelegramIdOrNull(Long telegramId) {
        return repository.findByTelegramId(telegramId).orElse(null);
    }

    /**
     * Обновить время утреннего уведомления (в локальном времени пользователя).
     */
    public UserNotificationSettings updateDailyTime(Long telegramId, LocalTime dailyTime) {
        UserNotificationSettings settings = getOrCreate(telegramId);
        settings.setDailyTime(dailyTime);
        settings.setDailyEnabled(true);
        settings.setLastDailyNotified(null);
        log.info("Update dailyTime for user {} to {}", telegramId, dailyTime);
        return repository.save(settings);
    }

    /**
     * Включить / выключить утренние уведомления.
     */
    public UserNotificationSettings updateDailyEnabled(Long telegramId, boolean enabled) {
        UserNotificationSettings settings = getOrCreate(telegramId);
        settings.setDailyEnabled(enabled);
        log.info("Update dailyEnabled for user {} to {}", telegramId, enabled);
        return repository.save(settings);
    }

    /**
     * Обновить смещение от UTC (UTC+N).
     */
    public UserNotificationSettings updateUtcOffset(Long telegramId, Integer utcOffsetHours) throws TokenRefreshException, IOException {
        UserNotificationSettings settings = getOrCreate(telegramId);
        settings.setUtcOffsetHours(utcOffsetHours);
        log.info("Update utcOffsetHours for user {} to {}", telegramId, utcOffsetHours);
        return repository.save(settings);

    }

    /**
     * Пометить, что утреннее уведомление уже отправлено "сегодня"
     * относительно часового пояса пользователя.
     */
    public void markDailyNotified(Long telegramId) {
        // Берём (или создаём) настройки пользователя
        UserNotificationSettings settings = getOrCreate(telegramId);

        // Смещение пользователя от UTC в часах (например, 3 = UTC+3)
        Integer offset = settings.getUtcOffsetHours();
        if (offset == null) {
            // Если смещение не задано, используем дефолт (UTC).
            // Здесь можно вместо 0 бросать исключение или логировать варнинг,
            // если хочешь заставить пользователя явно выбрать часовой пояс.
            offset = 0;
        }

        // Превращаем смещение в ZoneOffset, чтобы считать локальное время пользователя
        ZoneOffset zoneOffset = ZoneOffset.ofHours(offset);

        // Текущее время "сейчас" для пользователя с учётом его смещения
        // Instant.now() — время в UTC, а ofInstant(..., zoneOffset) переводит его в локальное
        LocalDate userToday = LocalDateTime
                .ofInstant(Instant.now(), zoneOffset)
                .toLocalDate(); // Берём только дату (без времени)

        // Сохраняем дату, за которую утреннее уведомление уже отправлено
        settings.setLastDailyNotified(userToday);
        repository.save(settings);

        // Логируем факт обновления (для дебага и мониторинга)
        log.info("Marked daily notified for user {} at local date {}", telegramId, userToday);
    }

    /**
     * Получить всех пользователей с включенным утренним уведомлением.
     * Используется шедулером.
     */
    public List<UserNotificationSettings> findAllEnabled() {
        return repository.findAllByDailyEnabledTrue();
    }
}
