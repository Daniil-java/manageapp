package com.kuklin.manageapp.bots.caloriebot.services;

import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для пользовательских настроек
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSettingsService {
    private final UserSettingsRepository userSettingsRepository;

    /**
     * Устанавливает таймзону пользователя с предварительной валидацией.
     */
    @Transactional
    public UserSettings setTimeZoneOrNull(Long userId, String tz) {
        // 1. Валидация
        if (!isValidTimeZone(tz)) {
            log.error("Attempt to set invalid time zone: {} for user: {}", tz, userId);
            return null;
        }

        // 2. Получение и обновление
        UserSettings settings = getOrCreate(userId);
        settings.setTimezoneId(tz);
        settings.setUpdatedAt(Instant.now());

        log.info("Timezone for user {} successfully updated to {}", userId, tz);
        return userSettingsRepository.save(settings);
    }

    /**
     * Проверяет, существует ли такая таймзона в системе.
     */
    public boolean isValidTimeZone(String tz) {
        if (tz == null || tz.isBlank()) return false;
        try {
            ZoneId.of(tz);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    //Получение и создание настроек пользователя
    public UserSettings getOrCreate(Long userId) {
        Optional<UserSettings> optSettings = userSettingsRepository.findById(userId);

        if (optSettings.isPresent()) {
            return optSettings.get();
        }

        return userSettingsRepository.save(UserSettings.createDefaultSettings(userId));
    }

    // ---------- DAILY SUMMARY ----------

    //Включение отчета о дневных итогах
    @Transactional
    public UserSettings enableDailySummaryOrNull(Long userId) {
        UserSettings settings = getOrCreate(userId);
        settings.setDailySummaryEnabled(true);
        settings.setUpdatedAt(Instant.now());
        return userSettingsRepository.save(settings);
    }

    //Установки времени уведомления ежедневного отчета
    @Transactional
    public UserSettings setDailySummaryTimeOrNull(Long userId, int hour) {
        if (!validateHour(hour)) {
            return null;
        }
        UserSettings settings = getOrCreate(userId);
        settings.setDailySummaryHour(hour);
        settings.setUpdatedAt(Instant.now());
        return userSettingsRepository.save(settings);
    }

    //Отключение уведомления ежедневного отчета
    @Transactional
    public UserSettings disableDailySummary(Long userId) {
        UserSettings settings = getOrCreate(userId);
        settings.setDailySummaryEnabled(false);
        settings.setUpdatedAt(Instant.now());
        return userSettingsRepository.save(settings);
    }

    /**
     * Проверка: нужно ли сейчас отправить daily summary этому пользователю.
     * nowUtc – текущее время в UTC (Instant.now()).
     */
    public boolean shouldSendDailySummary(UserSettings settings, Instant nowUtc) {
        if (!settings.isRemindersEnabled() || !settings.isDailySummaryEnabled()) {
            return false;
        }

        ZoneId zoneId = settings.getZoneId();
        ZonedDateTime nowLocal = nowUtc.atZone(zoneId);

        // Проверяем только по часу (минуты = 0)
        if (nowLocal.getHour() != settings.getDailySummaryHour() || nowLocal.getMinute() != 0) {
            return false;
        }

        // Проверка, что сегодня ещё не отправляли
        Instant last = settings.getDailyLastReminder();
        if (last == null) {
            return true;
        }

        ZonedDateTime lastLocal = last.atZone(zoneId);
        return !isSameLocalDay(nowLocal, lastLocal);
    }


    @Transactional
    public void markDailySummarySent(UserSettings settings, Instant sentAtUtc) {
        settings.setDailyLastReminder(sentAtUtc);
        settings.setUpdatedAt(Instant.now());
        userSettingsRepository.save(settings);
    }

    // ---------- MEAL REMINDER ----------

    @Transactional
    public UserSettings enableMealReminderOrNull(Long userId) {
        UserSettings settings = getOrCreate(userId);
        settings.setMealReminderEnabled(true);
        settings.setUpdatedAt(Instant.now());
        return userSettingsRepository.save(settings);
    }

    @Transactional
    public UserSettings setMealReminderTimeOrNull(Long userId, int intervalMinutes) {
        if (!validatePositiveInterval(intervalMinutes)) {
            return null;
        }
        UserSettings settings = getOrCreate(userId);
        settings.setMealReminderIntervalMinutes(intervalMinutes);
        settings.setUpdatedAt(Instant.now());
        return userSettingsRepository.save(settings);
    }

    @Transactional
    public UserSettings disableMealReminder(Long userId) {
        UserSettings settings = getOrCreate(userId);
        settings.setMealReminderEnabled(false);
        settings.setUpdatedAt(Instant.now());
        return userSettingsRepository.save(settings);
    }

    /**
     * Проверка: можно ли отправить напоминание "ты давно не ел" (антиспам по последнему напоминанию).
     *
     * @param settings             настройки пользователя
     * @param nowUtc               текущее UTC
     * @param minutesSinceLastMeal сколько минут прошло с последнего приема пищи
     */
    public boolean shouldSendMealReminder(UserSettings settings,
                                          Instant nowUtc,
                                          long minutesSinceLastMeal) {
        if (!settings.isRemindersEnabled() || !settings.isMealReminderEnabled()) {
            return false;
        }

        int interval = settings.getMealReminderIntervalMinutes();
        if (minutesSinceLastMeal < interval) {
            return false;
        }

        Instant lastReminder = settings.getMealLastReminderUtc();
        if (lastReminder == null) {
            return true;
        }

        long sinceLastReminder = Duration.between(lastReminder, nowUtc).toMinutes();
        // не шлём чаще, чем раз в interval минут
        return sinceLastReminder >= interval;
    }

    @Transactional
    public void markMealReminderSent(UserSettings settings, Instant sentAtUtc) {
        settings.setMealLastReminderUtc(sentAtUtc);
        settings.setUpdatedAt(Instant.now());
        userSettingsRepository.save(settings);
    }

    // ---------- COMMON ----------

    private boolean validateHour(int hour) {
        return hour >= 0 && hour <= 23;
    }

    private boolean validatePositiveInterval(int minutes) {
        return minutes > 0;
    }

    private boolean isSameLocalDay(ZonedDateTime a, ZonedDateTime b) {
        return a.getYear() == b.getYear()
                && a.getMonthValue() == b.getMonthValue()
                && a.getDayOfMonth() == b.getDayOfMonth();
    }

    public List<UserSettings> getAllUserSettingWithEnabledDailySummary(boolean isEnabled) {
        return userSettingsRepository.findAllByDailySummaryEnabled(isEnabled);
    }

    public List<UserSettings> getAllUserSettingWithEnabledMealReminder() {
        return userSettingsRepository.findAllUsersReadyForMealReminder(Instant.now());
    }

    public void updateDailyLastReminder(Long userId) {
        UserSettings userSettings = getOrCreate(userId);
        userSettings.setDailyLastReminder(Instant.now());
        userSettingsRepository.save(userSettings);
    }

    public void updateMealLastReminder(Long userId) {
        UserSettings userSettings = getOrCreate(userId);
        userSettings.setMealLastReminderUtc(Instant.now());
        userSettingsRepository.save(userSettings);
    }
}
