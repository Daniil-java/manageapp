package com.kuklin.manageapp.bots.caloriebot.onboarding.telegram;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.dish.Dish;
import com.kuklin.manageapp.bots.caloriebot.dish.telegram.DishKeyboards;
import com.kuklin.manageapp.bots.caloriebot.dish.telegram.DishKeyboards.PortionCallbacks;
import com.kuklin.manageapp.bots.caloriebot.dish.telegram.DishTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.TelegramBot;
import com.kuklin.manageapp.common.telegram.keyboard.ButtonStyle;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.Instant;

/**
 * «👀 Посмотреть пример» — под приветствием /start и в напоминаниях новичку. Присылает карточку блюда-примера
 * («Овсянка с бананом и кофе») в том же виде, что настоящая: шкала за день по условной норме (до блюда уже
 * «съеден» завтрак и перекус), те же кнопки. Кнопки работают на самом примере: вес ±10% и порции ➖➕
 * пересчитывают карточку на месте, «Удалить» и «В избранное» — всплывающей подсказкой, что они делают в настоящей
 * записи. Следом — «понажимай кнопки», призыв заполнить профиль (если не заполнен, с кнопкой) и «теперь твоя очередь».
 * <p>
 * ИИ не вызывается, в базу блюдо не пишется, лимиты не тратятся: вес порции и число порций — в callbackData,
 * КБЖУ считаются от исходной порции. Первое нажатие «Посмотреть пример» — в user_settings.first_dish_sample_at.
 * На нажатия отвечает сам (CalorieTelegramBot.isAnsweredByHandler) — ради подсказок.
 * <p>
 * callbackData: «/sampledish» — прислать пример; «/sampledish &lt;вес порции&gt; &lt;порций&gt; &lt;действие&gt;» —
 * кнопка под карточкой, действие: w-10 / w10 — вес, p&lt;N&gt; — порций, del, fav.
 */
@Component
@RequiredArgsConstructor
public class SampleDishCalorieUpdateHandler implements CalorieBotUpdateHandler {
    static final String SAMPLE_EMOJI = "🥣";
    // Исходная порция примера: 380 г — 410 ккал, Б 12, Ж 9, У 70
    static final int BASE_PORTION_WEIGHT = 380;
    static final int BASE_CALORIES = 410;
    static final int BASE_PROTEINS = 12;
    static final int BASE_FATS = 9;
    static final int BASE_CARBS = 70;
    // «Съедено» до примера — чтобы шкала была не пустой: завтрак и перекус
    static final Dish EATEN_BEFORE = new Dish().setCalories(760).setProteins(38).setFats(30).setCarbohydrates(95);
    // Условная норма для шкалы (её же называет start.sample.after)
    static final int NORM_CALORIES = 2000;
    static final int NORM_PROTEINS = 110;
    static final int NORM_FATS = 67;
    static final int NORM_CARBS = 240;

    static final int MIN_PORTION_WEIGHT = 40;
    static final int MAX_PORTION_WEIGHT = 2000;
    static final int MAX_PORTIONS = 10;
    static final int WEIGHT_STEP_PERCENT = 10;

    static final String ACTION_WEIGHT_MINUS = "w-" + WEIGHT_STEP_PERCENT;
    static final String ACTION_WEIGHT_PLUS = "w" + WEIGHT_STEP_PERCENT;
    static final String ACTION_PORTIONS = "p";
    static final String ACTION_DELETE = "del";
    static final String ACTION_FAVORITE = "fav";
    private static final int CARD_BUTTON_PARTS = 4;

    private final CalorieTelegramBot calorieTelegramBot;
    private final DishTexts dishTexts;
    private final DishKeyboards dishKeyboards;
    private final CalorieTexts texts;
    private final UserSettingsService userSettingsService;
    private final UserNutritionProfileService userNutritionProfileService;

    /** Вес одной порции и число порций карточки-примера. */
    record SampleState(int portionWeight, int portions) {
        static final SampleState INITIAL = new SampleState(BASE_PORTION_WEIGHT, 1);
    }

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long appUserId = telegramUser.getAppUserId();
        if (!update.hasCallbackQuery()) {
            sendSample(update.getMessage().getChatId(), appUserId);
            return;
        }
        CallbackQuery query = update.getCallbackQuery();
        String[] parts = query.getData().split(TelegramBot.DEFAULT_DELIMETER);
        if (parts.length < CARD_BUTTON_PARTS) {
            calorieTelegramBot.answerCallbackQuery(query, null);
            sendSample(query.getMessage().getChatId(), appUserId);
            return;
        }
        handleCardButton(query, appUserId, parts);
    }

    private void sendSample(Long chatId, Long appUserId) {
        userSettingsService.claimFirstDishSample(appUserId, Instant.now());
        AppLanguage language = texts.languageOf(appUserId);
        calorieTelegramBot.sendReturnedMessage(chatId, buildCardText(SampleState.INITIAL, language),
                buildCardKeyboard(SampleState.INITIAL, language), null);

        boolean profileFilled = Boolean.TRUE.equals(
                userNutritionProfileService.getOrCreateProfile(appUserId).checkTargetCalculateParams());
        calorieTelegramBot.sendReturnedMessage(chatId,
                texts.get(language, profileFilled
                        ? BotTextKeys.START_SAMPLE_AFTER_PROFILE_FILLED : BotTextKeys.START_SAMPLE_AFTER),
                profileFilled ? null : buildFillProfileKeyboard(language), null);
    }

    /** Кнопка под карточкой: вес и порции — перерисовать карточку, «Удалить» / «В избранное» — подсказка. */
    private void handleCardButton(CallbackQuery query, Long appUserId, String[] parts) {
        AppLanguage language = texts.languageOf(appUserId);
        String action = parts[3];
        if (ACTION_DELETE.equals(action) || ACTION_FAVORITE.equals(action)) {
            calorieTelegramBot.answerCallbackQuery(query, texts.get(language, ACTION_DELETE.equals(action)
                    ? BotTextKeys.START_SAMPLE_HINT_DELETE : BotTextKeys.START_SAMPLE_HINT_FAVORITE));
            return;
        }
        calorieTelegramBot.answerCallbackQuery(query, null);

        SampleState state = applyActionOrNull(parseStateOrNull(parts), action);
        MaybeInaccessibleMessage message = query.getMessage();
        if (state == null) return;
        calorieTelegramBot.sendEditMessage(message.getChatId(), buildCardText(state, language),
                message.getMessageId(), buildCardKeyboard(state, language));
    }

    private static SampleState parseStateOrNull(String[] parts) {
        try {
            return new SampleState(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Новое состояние после нажатия; null — данные кнопки битые или значение вышло за пределы. */
    static SampleState applyActionOrNull(SampleState state, String action) {
        if (state == null) return null;
        SampleState next;
        if (ACTION_WEIGHT_MINUS.equals(action) || ACTION_WEIGHT_PLUS.equals(action)) {
            int percent = ACTION_WEIGHT_PLUS.equals(action) ? WEIGHT_STEP_PERCENT : -WEIGHT_STEP_PERCENT;
            next = new SampleState(Dish.scale(state.portionWeight(), 1.0 + percent / 100.0), state.portions());
        } else if (action.startsWith(ACTION_PORTIONS)) {
            try {
                next = new SampleState(state.portionWeight(), Integer.parseInt(action.substring(ACTION_PORTIONS.length())));
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }
        boolean valid = next.portionWeight() >= MIN_PORTION_WEIGHT && next.portionWeight() <= MAX_PORTION_WEIGHT
                && next.portions() >= 1 && next.portions() <= MAX_PORTIONS;
        return valid ? next : null;
    }

    /** Блюдо-пример в этом состоянии: КБЖУ — от исходной порции пропорционально весу и числу порций. */
    Dish buildSampleDish(SampleState state, AppLanguage language) {
        double multiplier = state.portionWeight() / (double) BASE_PORTION_WEIGHT * state.portions();
        return new Dish()
                .setName(texts.get(language, BotTextKeys.START_SAMPLE_DISH_NAME))
                .setEmojiIcon(SAMPLE_EMOJI)
                .setCalories(Dish.scale(BASE_CALORIES, multiplier))
                .setProteins(Dish.scale(BASE_PROTEINS, multiplier))
                .setFats(Dish.scale(BASE_FATS, multiplier))
                .setCarbohydrates(Dish.scale(BASE_CARBS, multiplier))
                .setPortionWeight(state.portionWeight())
                .setPortions(state.portions());
    }

    String buildCardText(SampleState state, AppLanguage language) {
        UserNutritionProfile norms = new UserNutritionProfile()
                .setCaloriesNormPerDay(NORM_CALORIES)
                .setProteinsNormGramsPerDay(NORM_PROTEINS)
                .setFatsNormGramsPerDay(NORM_FATS)
                .setCarbsNormGramsPerDay(NORM_CARBS);
        return dishTexts.buildSampleCardText(buildSampleDish(state, language), EATEN_BEFORE, norms, language);
    }

    InlineKeyboardMarkup buildCardKeyboard(SampleState state, AppLanguage language) {
        String base = Command.CALORIE_SAMPLE_DISH.getCommandText() + TelegramBot.DEFAULT_DELIMETER
                + state.portionWeight() + TelegramBot.DEFAULT_DELIMETER
                + state.portions() + TelegramBot.DEFAULT_DELIMETER;
        PortionCallbacks callbacks = new PortionCallbacks(
                base + ACTION_WEIGHT_MINUS,
                base + ACTION_WEIGHT_PLUS,
                base + ACTION_PORTIONS + (state.portions() - 1),
                base + ACTION_PORTIONS + (state.portions() + 1),
                base + ACTION_DELETE,
                base + ACTION_FAVORITE);
        return dishKeyboards.buildPortionKeyboard(buildSampleDish(state, language), language, callbacks);
    }

    private InlineKeyboardMarkup buildFillProfileKeyboard(AppLanguage language) {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(texts.get(language, BotTextKeys.START_FLOW_BUTTON_FILL_PROFILE),
                        Command.CALORIE_PROFILE_DIALOGUE.getCommandText(), ButtonStyle.PRIMARY))
                .build();
    }

    /** Кнопка «👀 Посмотреть пример» — для приветствия и напоминаний. */
    public InlineKeyboardButton buildSampleButton(AppLanguage language) {
        return TelegramKeyboard.button(texts.get(language, BotTextKeys.START_BUTTON_SAMPLE),
                Command.CALORIE_SAMPLE_DISH.getCommandText());
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_SAMPLE_DISH.getCommandText();
    }
}
