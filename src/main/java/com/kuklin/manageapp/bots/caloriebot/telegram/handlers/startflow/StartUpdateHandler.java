package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.startflow;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReferralRewardService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UtmService;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.models.landing.CalculatorStartParam;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieSiteLinks;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.KeyboardCalorieUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalculatorProfileUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.texts.SubscriptionTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.ButtonStyle;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * /start. Кто ещё не выбирал язык — сначала вопрос о языке (на всех языках сразу), приветствие и остальное —
 * после выбора (StartLanguageUpdateHandler → continueStart). Кто выбирал — приветствие сразу.
 */
@Component
@RequiredArgsConstructor
public class StartUpdateHandler implements CalorieBotUpdateHandler {

    private final UserSubscriptionService userSubscriptionService;
    private final CalorieTelegramBot calorieTelegramBot;
    private final SubscriptionTexts subscriptionTexts;
    private final UserNutritionProfileService userNutritionProfileService;
    private final CalorieNutritionProfileUpdateHandler calorieNutritionProfileUpdateHandler;
    private final UtmService utmService;
    private final ReferralRewardService referralRewardService;
    private final KeyboardCalorieUpdateHandler keyboardCalorieUpdateHandler;
    private final CalculatorProfileUpdateHandler calculatorProfileUpdateHandler;
    private final CalorieMenuButtons menuButtons;
    private final CalorieTexts texts;

    /**
     * Что /start должен показать после выбора языка, по appUserId. В памяти: после перезапуска
     * выбор языка всё равно продолжит /start, только без предложения калькулятора и сообщения о пробном периоде.
     */
    private final Map<Long, PendingStart> pendingStarts = new ConcurrentHashMap<>();

    /** @param partnerBonusDays дней Premium по партнёрской ссылке (UtmService.processClick), 0 — не было */
    private record PendingStart(CalculatorStartParam.Calculator calculator, UserSubscription trial, int partnerBonusDays) {
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

        PendingStart start = new PendingStart(param.calculator(), trial, partnerBonusDays);
        if (!texts.hasChosenLanguage(appUserId)) {
            pendingStarts.put(appUserId, start);
            sendLanguageQuestion(chatId, update.getMessage().getFrom().getLanguageCode());
            return;
        }
        sendWelcome(chatId, appUserId, start);
    }

    /** Язык выбран в ответ на вопрос из /start — показываем то, что /start отложил. */
    public void continueStart(Long chatId, Long appUserId) {
        PendingStart pending = pendingStarts.remove(appUserId);
        sendWelcome(chatId, appUserId, pending == null ? new PendingStart(null, null, 0) : pending);
    }

    /**
     * «На каком языке будем общаться?» — каждым языком по абзацу и кнопка на каждый язык.
     * Язык Telegram пользователя — первым: его кнопка ближе, абзац сверху.
     */
    private void sendLanguageQuestion(Long chatId, String telegramLanguageCode) {
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

    private void sendWelcome(Long chatId, Long appUserId, PendingStart start) {
        CalculatorStartParam.Calculator calculator = start.calculator();
        UserSubscription trial = start.trial();
        AppLanguage language = texts.languageOf(appUserId);
        keyboardCalorieUpdateHandler.updateKeyboard(chatId, appUserId);
        calorieTelegramBot.sendReturnedMessage(
                chatId,
                texts.get(language, BotTextKeys.START_WELCOME,
                        CalorieSiteLinks.buildPrivacyUrl(language), CalorieSiteLinks.buildTermsUrl(language)),
                getStartFlowKeyboard(appUserId),
                null
        );
        if (calculator != null) {
            calculatorProfileUpdateHandler.offer(chatId, appUserId, calculator);
        }

        //Сообщение с просьбой заполнить профиль
//        sendUserNutritionFillRequest(update, telegramUser);

        if (trial != null) {
            calorieTelegramBot.sendReturnedMessage(chatId, subscriptionTexts.buildSubscriptionEntryText(appUserId, trial));
        }
        if (start.partnerBonusDays() > 0) {
            calorieTelegramBot.sendReturnedMessage(chatId,
                    texts.get(language, BotTextKeys.START_PARTNER_BONUS, start.partnerBonusDays()));
        }
    }

    private InlineKeyboardMarkup getStartFlowKeyboard(Long appUserId) {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(
                        texts.get(appUserId, BotTextKeys.COMMON_BUTTON_NEXT),
                        Command.CALORIE_START_FLOW_1.getCommandText(),
                        ButtonStyle.PRIMARY))
                .row(menuButtons.buildCloseButton(appUserId))
                .build();
    }

    /**
     * Параметр /start: UTM-код или данные калькулятора лендинга с UTM-кодом в хвосте
     * (см. CalculatorStartParam).
     */
    private static CalculatorStartParam parseStartParam(Update update) {
        String[] parts = update.getMessage().getText().split(TelegramBot.DEFAULT_DELIMETER);
        return CalculatorStartParam.parse(parts.length < 2 ? null : parts[1]);
    }

    //Сообщение с просьбой заполнить профиль
    private void sendUserNutritionFillRequest(Update update, TelegramUser telegramUser) {
        UserNutritionProfile profile = userNutritionProfileService
                .getOrCreateProfile(telegramUser.getAppUserId());
        if (!profile.checkTargetCalculateParams()) {
            calorieNutritionProfileUpdateHandler.handle(update, telegramUser);
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_START.getCommandText();
    }
}
