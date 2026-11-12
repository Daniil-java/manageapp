package com.kuklin.manageapp.bots.caloriebot.onboarding.telegram;

import com.kuklin.manageapp.bots.caloriebot.referral.ReferralRewardService;
import com.kuklin.manageapp.bots.caloriebot.referral.UtmService;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.onboarding.CalculatorStartParam;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.web.CalorieSiteLinks;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.profile.telegram.CalculatorProfileUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.TelegramBot;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * /start. Кто ещё не выбирал язык — сначала вопрос о языке (на всех языках сразу), приветствие и остальное —
 * после выбора (StartLanguageUpdateHandler → continueStart). Что показать в приветствии, до выбора лежит
 * в user_settings.pending_start_*. Кто выбирал — приветствие сразу.
 */
@Component
@RequiredArgsConstructor
public class StartUpdateHandler implements CalorieBotUpdateHandler {

    private final UserSubscriptionService userSubscriptionService;
    private final CalorieTelegramBot calorieTelegramBot;
    private final UtmService utmService;
    private final ReferralRewardService referralRewardService;
    private final CalculatorProfileUpdateHandler calculatorProfileUpdateHandler;
    private final CalorieMenuButtons menuButtons;
    private final CalorieTexts texts;
    private final UserSettingsService userSettingsService;
    private final SampleDishCalorieUpdateHandler sampleDishHandler;

    /**
     * Что показать в приветствии /start.
     *
     * @param trialDays        дней пробного периода, 0 — не выдавали (уже был)
     * @param partnerBonusDays дней Premium по партнёрской ссылке (UtmService.processClick), 0 — не было
     */
    record PendingStart(CalculatorStartParam.Calculator calculator, int trialDays, int partnerBonusDays) {
    }

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage().getChatId();
        Long appUserId = telegramUser.getAppUserId();

        CalculatorStartParam param = parseStartParam(update);
        //Пробный период. Если null - значит пробный период уже был у пользователя
        UserSubscription trial = userSubscriptionService
                .createSubscriptionByFreePlanOrNull(appUserId, BotIdentifier.CALORIE_BOT);
        // UTM засчитываем сразу — не теряем переход, даже если язык так и не выберут.
        // Пробный период выдаётся только при первом /start — по нему и узнаём нового пользователя
        int partnerBonusDaysDue = param.utmCode() == null
                ? 0
                : utmService.processClick(param.utmCode(), appUserId, trial != null);
        int partnerBonusDays = referralRewardService.grantPartnerBonusOrZero(appUserId, partnerBonusDaysDue);

        PendingStart start = new PendingStart(param.calculator(), getTrialDays(trial), partnerBonusDays);
        if (!texts.hasChosenLanguage(appUserId)) {
            savePendingStart(appUserId, start);
            sendLanguageQuestion(chatId, update.getMessage().getFrom().getLanguageCode());
            return;
        }
        sendWelcome(chatId, appUserId, start);
    }

    /** Язык выбран в ответ на вопрос из /start — показываем то, что /start отложил. */
    public void continueStart(Long chatId, Long appUserId) {
        sendWelcome(chatId, appUserId, takePendingStart(appUserId));
    }

    /**
     * Отложить приветствие до выбора языка — в базу, чтобы деплой между /start и выбором его не потерял.
     * Пустое (0, нет калькулятора) не затирает отложенное прошлым /start.
     */
    private void savePendingStart(Long appUserId, PendingStart start) {
        userSettingsService.savePendingStart(appUserId,
                start.calculator() == null ? null : start.calculator().encode(),
                start.trialDays() > 0 ? start.trialDays() : null,
                start.partnerBonusDays() > 0 ? start.partnerBonusDays() : null);
    }

    /** Отложенное /start из базы; в базе оно при этом очищается. */
    private PendingStart takePendingStart(Long appUserId) {
        return userSettingsService.takePendingStart(appUserId)
                .map(settings -> new PendingStart(
                        settings.getPendingStartCalculator() == null
                                ? null
                                : CalculatorStartParam.parse(settings.getPendingStartCalculator()).calculator(),
                        settings.getPendingStartTrialDays() == null ? 0 : settings.getPendingStartTrialDays(),
                        settings.getPendingStartBonusDays() == null ? 0 : settings.getPendingStartBonusDays()))
                .orElse(new PendingStart(null, 0, 0));
    }

    private static int getTrialDays(UserSubscription trial) {
        if (trial == null || trial.getStartAt() == null || trial.getEndAt() == null) return 0;
        return (int) Duration.between(trial.getStartAt(), trial.getEndAt()).plusHours(1).toDays();
    }

    /**
     * «На каком языке будем общаться?» — каждым языком по абзацу и кнопка на каждый язык.
     * Язык Telegram пользователя — первым: его кнопка ближе, абзац сверху. Ещё — напоминанием новичку, который
     * так и не выбрал язык (FirstDishNudgeSchedulerProcessor): ответ продолжает отложенный /start.
     */
    public void sendLanguageQuestion(Long chatId, String telegramLanguageCode) {
        Optional<AppLanguage> telegramLanguage = AppLanguage.fromCode(telegramLanguageCode);
        List<AppLanguage> languages = Arrays.stream(AppLanguage.values())
                .sorted(Comparator.comparing(language -> telegramLanguage.filter(language::equals).isEmpty()))
                .toList();

        String text = languages.stream()
                .map(language -> texts.get(language, BotTextKeys.LANGUAGE_CHOOSE_PROMPT, language.getFlag()))
                .collect(Collectors.joining("\n\n"));
        List<InlineKeyboardButton> buttons = languages.stream()
                .map(language -> TelegramKeyboard.button(language.getLabel(),
                        Command.CALORIE_START_LANGUAGE.getCommandText()
                                + TelegramBot.DEFAULT_DELIMETER + language.getCode()))
                .toList();

        calorieTelegramBot.sendReturnedMessage(chatId, text, TelegramKeyboard.builder().row(buttons).build(), null);
    }

    /**
     * Два сообщения. Первое — с нижней клавиатурой меню (её нельзя повесить на сообщение с кнопками под текстом):
     * что умеет бот, пробный период и бонус партнёра строками, условия. Второе, последним, чтобы было внизу
     * экрана, — призыв записать первое блюдо (фото, текст, голос) с кнопками «Посмотреть пример» и «Что ещё умею»
     * (/intro новым сообщением). Цель — первое блюдо: канал, приглашения и профиль — после него
     * (FirstDishFollowUpSender). С лендинга с калькулятором — между ними предложение сохранить профиль.
     */
    private void sendWelcome(Long chatId, Long appUserId, PendingStart start) {
        AppLanguage language = texts.languageOf(appUserId);
        calorieTelegramBot.sendReturnedMessage(chatId, buildWelcomeText(language, start),
                menuButtons.buildKeyboard(language), null);
        if (start.calculator() != null) {
            calculatorProfileUpdateHandler.offer(chatId, appUserId, start.calculator());
        }
        calorieTelegramBot.sendReturnedMessage(chatId, texts.get(language, BotTextKeys.START_FIRST_DISH_CALL),
                buildFirstDishCallKeyboard(language), null);
    }

    private InlineKeyboardMarkup buildFirstDishCallKeyboard(AppLanguage language) {
        return TelegramKeyboard.builder()
                .row(sampleDishHandler.buildSampleButton(language))
                .row(TelegramKeyboard.button(texts.get(language, BotTextKeys.START_BUTTON_MORE),
                        IntroCalorieUpdateHandler.NEW_MESSAGE_DATA))
                .build();
    }

    private String buildWelcomeText(AppLanguage language, PendingStart start) {
        List<String> parts = new ArrayList<>();
        parts.add(texts.get(language, BotTextKeys.START_WELCOME));
        if (start.trialDays() > 0) {
            parts.add(texts.get(language, BotTextKeys.START_WELCOME_TRIAL, (long) start.trialDays()));
        }
        if (start.partnerBonusDays() > 0) {
            parts.add(texts.get(language, BotTextKeys.START_PARTNER_BONUS, start.partnerBonusDays()));
        }
        parts.add(texts.get(language, BotTextKeys.START_LEGAL,
                CalorieSiteLinks.buildPrivacyUrl(language), CalorieSiteLinks.buildTermsUrl(language)));
        return String.join("\n\n", parts);
    }

    /**
     * Параметр /start: UTM-код или данные калькулятора лендинга с UTM-кодом в хвосте
     * (см. CalculatorStartParam).
     */
    private static CalculatorStartParam parseStartParam(Update update) {
        String[] parts = update.getMessage().getText().split(TelegramBot.DEFAULT_DELIMETER);
        return CalculatorStartParam.parse(parts.length < 2 ? null : parts[1]);
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_START.getCommandText();
    }
}
