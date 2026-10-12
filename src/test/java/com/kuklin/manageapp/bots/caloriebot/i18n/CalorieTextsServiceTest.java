package com.kuklin.manageapp.bots.caloriebot.i18n;

import com.kuklin.manageapp.bots.caloriebot.components.services.AiInputValidator;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.components.services.security.RequestSizeLimitFilter;
import com.kuklin.manageapp.bots.caloriebot.configurations.AiInputLimitsProperties;
import org.springframework.util.unit.DataSize;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorParams;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalorieTextsServiceTest {

    private static final Long RU_USER = 1L;
    private static final Long EN_USER = 2L;
    private static final Long NEW_USER = 3L;
    private static final Long ODD_USER = 4L;

    private UserSettingsService settings;
    private CalorieTextsService texts;

    @BeforeEach
    void setUp() {
        settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(RU_USER)).thenReturn("ru");
        when(settings.findLanguageCode(EN_USER)).thenReturn("en");
        when(settings.findLanguageCode(NEW_USER)).thenReturn(null);
        when(settings.findLanguageCode(ODD_USER)).thenReturn("xx");

        texts = new CalorieTextsService(new I18nProperties(), settings);
    }

    // ── Язык пользователя ──────────────────────────────────────────

    @Test
    void defaultLanguageIsRussian() {
        assertThat(texts.getDefaultLanguage()).isEqualTo(AppLanguage.RU);
    }

    @Test
    void chosenLanguageWins() {
        assertThat(texts.languageOf(EN_USER)).isEqualTo(AppLanguage.EN);
        assertThat(texts.hasChosenLanguage(EN_USER)).isTrue();
    }

    @Test
    void notChosenOrUnknownLanguageFallsBackToDefault() {
        assertThat(texts.languageOf(NEW_USER)).isEqualTo(AppLanguage.RU);
        assertThat(texts.languageOf(ODD_USER)).isEqualTo(AppLanguage.RU);
        assertThat(texts.languageOf(null)).isEqualTo(AppLanguage.RU);
        assertThat(texts.hasChosenLanguage(NEW_USER)).isFalse();
    }

    @Test
    void setLanguageStoresCodeAndResetStoresNull() {
        texts.setLanguage(NEW_USER, AppLanguage.EN);
        verify(settings).setLanguageCode(NEW_USER, "en");

        texts.setLanguage(NEW_USER, null);
        verify(settings).setLanguageCode(NEW_USER, null);
    }

    // ── Тексты ─────────────────────────────────────────────────────

    @Test
    void textComesInUserLanguageWithArguments() {
        assertThat(texts.get(RU_USER, BotTextKeys.DEV_LANG_CHANGED, "X")).isEqualTo("🌐 Язык переключён: X");
        assertThat(texts.get(EN_USER, BotTextKeys.DEV_LANG_CHANGED, "X")).isEqualTo("🌐 Language switched: X");
        assertThat(texts.get(NEW_USER, BotTextKeys.DEV_LANG_CHANGED, "X")).isEqualTo("🌐 Язык переключён: X");
    }

    @Test
    void unknownKeyReturnsKeyItself() {
        assertThat(texts.get(AppLanguage.EN, "no.such.key")).isEqualTo("no.such.key");
    }

    // ── Ошибки ─────────────────────────────────────────────────────

    @Test
    void errorTextUsesParamsOfException() {
        ErrorResponseException e = new ErrorResponseException(ErrorStatus.TEXT_TOO_LONG, ErrorParams.max(4096));
        assertThat(texts.error(RU_USER, e)).isEqualTo("Слишком длинный текст. Максимум — 4 096 символов.");
        assertThat(texts.error(EN_USER, e)).isEqualTo("Text is too long. Maximum is 4,096 characters.");
    }

    @Test
    void apiMessageIsEnglishOneLineWhateverUserLanguage() {
        ErrorResponseException limit = new ErrorResponseException(ErrorStatus.DISH_DAILY_LIMIT, ErrorParams.max(1000));
        assertThat(texts.errorForApi(limit)).isEqualTo("Too many dishes added in the last 24 hours (max 1,000). Try again later.");

        // В словаре — с переносами для бота, в API — одной строкой
        ErrorResponseException photo = new ErrorResponseException(ErrorStatus.PHOTO_NOT_RECOGNIZED_COUNTED);
        assertThat(texts.errorForApi(photo))
                .isEqualTo("🤔 No food found in the photo. This scan was counted. Take a clear, close-up photo of the dish in good light or describe it in text.");
    }

    @Test
    void errorWithoutParams() {
        assertThat(texts.error(RU_USER, ErrorStatus.FAVORITE_DISH_ALREADY_EXISTS))
                .isEqualTo("Блюдо с таким названием уже есть в сохранённых.");
    }

    @Test
    void waitTimeIsSpelledWithHoursAndMinutes() {
        assertThat(dailyLimit(RU_USER, 40)).isEqualTo("Дневной лимит ИИ исчерпан (100 в сутки). Попробуйте через 1 минуту.");
        assertThat(dailyLimit(RU_USER, 25 * 60)).endsWith("через 25 минут.");
        assertThat(dailyLimit(RU_USER, 3600)).endsWith("через 1 час.");
        assertThat(dailyLimit(RU_USER, 3700)).endsWith("через 1 час 2 минуты.");
        assertThat(dailyLimit(RU_USER, 5 * 3600 + 60)).endsWith("через 5 часов 1 минуту.");
        assertThat(dailyLimit(EN_USER, 2 * 3600 + 300)).endsWith("in 2 hours 5 minutes.");
    }

    /** Имена params в коде совпадают с подстановками словарей: после подстановки не остаётся {…}. */
    @Test
    void realValidatorErrorsRenderWithoutPlaceholders() {
        AiInputLimitsProperties limits = new AiInputLimitsProperties();
        limits.setTextMaxLength(10);
        limits.setPhotoCommentMaxLength(5);
        limits.setPhotoMaxSize(DataSize.ofBytes(100));
        limits.setPhotoFormats(List.of("jpeg", "png"));
        limits.setVoiceMaxSize(DataSize.ofBytes(100));
        limits.setVoiceMaxDuration(Duration.ofMinutes(2));
        limits.setVoiceFormats(List.of("ogg", "webm"));
        limits.setRequestMaxSize(DataSize.ofMegabytes(8));
        AiInputValidator validator = new AiInputValidator(limits);
        String b64 = Base64.getEncoder().encodeToString(new byte[101]);

        List<ErrorResponseException> errors = List.of(
                catchError(() -> validator.validateText("12345678901")),
                catchError(() -> validator.toPhotoDataUrl(b64, "123456")),
                catchError(() -> validator.toPhotoDataUrl(b64, null)),
                catchError(() -> validator.toVoiceInput(b64, "exe")),
                catchError(() -> validator.toVoiceInput(b64, "ogg")),
                RequestSizeLimitFilter.tooLarge(limits));

        for (ErrorResponseException e : errors) {
            assertThat(e.getParams()).as("params of %s", e.getErrorStatus()).isNotEmpty();
            for (Long user : List.of(RU_USER, EN_USER)) {
                assertThat(texts.error(user, e)).as("%s for user %d", e.getErrorStatus(), user).doesNotContain("{", "}");
            }
        }
        assertThat(texts.error(RU_USER, errors.get(4))).isEqualTo("Голосовое слишком длинное. Максимум — 0 МБ (около 2 мин).");
    }

    private static ErrorResponseException catchError(Runnable action) {
        try {
            action.run();
        } catch (ErrorResponseException e) {
            return e;
        }
        throw new AssertionError("Expected ErrorResponseException");
    }

    private String dailyLimit(Long user, long seconds) {
        return texts.error(user, new ErrorResponseException(ErrorStatus.AI_DAILY_LIMIT,
                ErrorParams.dailyLimit(seconds, 100)));
    }

    // ── Формат ICU ─────────────────────────────────────────────────

    @Test
    void pluralsFollowLanguageRules() {
        String ru = "{0, plural, one{# день} few{# дня} many{# дней} other{# дня}}";
        assertThat(CalorieTextsService.format(AppLanguage.RU, ru, 1)).isEqualTo("1 день");
        assertThat(CalorieTextsService.format(AppLanguage.RU, ru, 3)).isEqualTo("3 дня");
        assertThat(CalorieTextsService.format(AppLanguage.RU, ru, 5)).isEqualTo("5 дней");
        assertThat(CalorieTextsService.format(AppLanguage.RU, ru, 21)).isEqualTo("21 день");

        String en = "{0, plural, one{# day} other{# days}}";
        assertThat(CalorieTextsService.format(AppLanguage.EN, en, 1)).isEqualTo("1 day");
        assertThat(CalorieTextsService.format(AppLanguage.EN, en, 5)).isEqualTo("5 days");
    }

    @Test
    void singleApostropheIsKeptAsIs() {
        assertThat(CalorieTextsService.format(AppLanguage.EN, "Don't forget {0}", "lunch")).isEqualTo("Don't forget lunch");
    }
}
