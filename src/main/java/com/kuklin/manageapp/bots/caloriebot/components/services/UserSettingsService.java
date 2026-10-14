package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UserSettingsRepository;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.UserSettingsDto;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorParams;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    // Интервал напоминаний о еде: шедулер проверяет раз в 20 минут, чаще часа — спам, реже суток — смысла нет
    static final int MEAL_REMINDER_INTERVAL_MIN = 60;
    static final int MEAL_REMINDER_INTERVAL_MAX = 24 * 60;

    private final UserSettingsRepository userSettingsRepository;
    private final PlatformTransactionManager transactionManager;

    // Для альтернативного варианта getOrCreate через самовызов — см. комментарий под методом.
    // Ссылка на собственный прокси — для вызова @Transactional-метода изнутри класса
//    @Autowired
//    @Lazy
//    private UserSettingsService self;

    public UserSettingsDto getSettingsDto(Long userId) {
        UserSettings settings = getOrCreate(userId);
        return UserSettingsDto.fromEntity(settings);
    }

    @Transactional
    public UserSettingsDto updateSettingsDto(Long userId, UserSettingsDto dto) {
        validateOrThrow(dto);
        UserSettings settings = getOrCreate(userId);

        // Обновляем поля сущности данными из DTO
        settings = dto.mergeToEntity(settings);
        return UserSettingsDto.fromEntity(userSettingsRepository.save(settings));
    }

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

    /** Код языка из настроек или null, если пользователь язык не выбирал. Настройки не создаёт. */
    public String findLanguageCode(Long userId) {
        return userSettingsRepository.findById(userId)
                .map(UserSettings::getLanguage)
                .orElse(null);
    }

    /** Сохраняет код языка; null — сбросить выбор (вернуться к языку по умолчанию). */
    @Transactional
    public void setLanguageCode(Long userId, String languageCode) {
        UserSettings settings = getOrCreate(userId);
        settings.setLanguage(languageCode);
        userSettingsRepository.save(settings);
        log.info("Language for user {} set to {}", userId, languageCode);
    }

    /**
     * Проверяет, существует ли такая таймзона в системе.
     */
    public static boolean isValidTimeZone(String tz) {
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

        // Первый заход нового пользователя: клиент шлёт несколько запросов параллельно, и каждый
        // пытается создать настройки. Создаём в отдельной транзакции — проигравший гонку ловит
        // нарушение первичного ключа, не ломая транзакцию вызывающего кода, и читает уже созданное.
        TransactionTemplate newTransaction = new TransactionTemplate(transactionManager);
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            newTransaction.executeWithoutResult(status ->
                    userSettingsRepository.saveAndFlush(UserSettings.createDefaultSettings(userId)));
        } catch (DataIntegrityViolationException e) {
            log.debug("Settings for user {} were created by a concurrent request", userId);
        }

        return userSettingsRepository.findById(userId).orElseThrow();
    }

    // Альтернатива TransactionTemplate — то же самое через самовызов и аннотацию.
    // Чтобы включить: раскомментировать поле self и метод ниже, а блок с TransactionTemplate
    // в getOrCreate заменить на:
    //
    //        try {
    //            // Через self, а не this — иначе вызов идёт мимо прокси и @Transactional не срабатывает
    //            self.createDefaultInNewTransaction(userId);
    //        } catch (DataIntegrityViolationException e) {
    //            log.debug("Settings for user {} were created by a concurrent request", userId);
    //        }
    //
    // Метод обязан быть public — прокси не перехватывает приватные методы.
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void createDefaultInNewTransaction(Long userId) {
//        userSettingsRepository.saveAndFlush(UserSettings.createDefaultSettings(userId));
//    }

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

    /**
     * Проверка настроек из API до сохранения (null — поле не меняется).
     * Без неё таймзона «Foo/Bar» ломала getZoneId() во всех запросах пользователя и в шедулерах,
     * час 99 — ежедневный отчёт, интервал 0 — напоминания о еде.
     */
    static void validateOrThrow(UserSettingsDto dto) {
        if (dto.getTimezone() != null && !isValidTimeZone(dto.getTimezone())) {
            throw new ErrorResponseException(ErrorStatus.INVALID_TIMEZONE);
        }
        if (dto.getDailySummaryHour() != null && !validateHour(dto.getDailySummaryHour())) {
            throw new ErrorResponseException(ErrorStatus.INVALID_DAILY_SUMMARY_HOUR);
        }
        Integer interval = dto.getMealReminderIntervalMinutes();
        if (interval != null && (interval < MEAL_REMINDER_INTERVAL_MIN || interval > MEAL_REMINDER_INTERVAL_MAX)) {
            throw new ErrorResponseException(ErrorStatus.INVALID_MEAL_REMINDER_INTERVAL,
                    ErrorParams.range(MEAL_REMINDER_INTERVAL_MIN, MEAL_REMINDER_INTERVAL_MAX));
        }
    }

    private static boolean validateHour(int hour) {
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
        return userSettingsRepository.findActiveSettingsForEnabledDailySummary(isEnabled, BotIdentifier.CALORIE_BOT);
    }

    public List<UserSettings> getAllUserSettingWithEnabledMealReminder() {
        return userSettingsRepository.findAllUsersReadyForMealReminder(Instant.now(), BotIdentifier.CALORIE_BOT.name());
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
