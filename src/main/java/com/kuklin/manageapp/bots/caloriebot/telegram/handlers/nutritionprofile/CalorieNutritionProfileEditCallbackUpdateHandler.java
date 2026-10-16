package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.CalorieNutritionProfileRecalculate;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditFieldHandler;
import com.kuklin.manageapp.bots.caloriebot.texts.NutritionProfileTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.EnumMap;
import java.util.Map;

import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.KeyboardTemplates.SET_CMD;

/*
* Обработчик команды редактирования полей
* Отвечает только на кодбэк
*
* Отвечает за:
* - возврат нужного обработчика
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CalorieNutritionProfileEditCallbackUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserNutritionProfileService userNutritionProfileService;
    private final NutritionProfileTexts profileTexts;
    private final CalorieTexts texts;
    private final Map<ProfileEditAction, ProfileEditFieldHandler> actionHandlers =
            new EnumMap<>(ProfileEditAction.class);

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;

        //Если в вернувшихся данных нет ничего кроме команды -
        // - возвращаем клавиатуру
        if (update.getCallbackQuery().getData().equals(getHandlerListName())) {
            sendMainMessage(update, telegramUser);
        } else {
            UserNutritionProfile profile = userNutritionProfileService
                    .getOrCreateProfile(telegramUser.getAppUserId());
            handleAction(profile, update, telegramUser);
            if (update.getCallbackQuery().getData().contains(SET_CMD)) {
                sendMainMessage(update, telegramUser);
            }
        }
    }

    //Отправка главного сообщения редактирования
    public void sendMainMessage(Update update, TelegramUser telegramUser) {
        UserNutritionProfile profile = userNutritionProfileService
                .getOrCreateProfile(telegramUser.getAppUserId());
        Message message = TelegramBot.getCallbackMessage(update.getCallbackQuery());

        calorieTelegramBot.sendEditMessage(
                message.getChatId(),
                buildMessageText(profile),
                message.getMessageId(),
                buildKeyboard(profile)
        );
    }

    private void handleAction(UserNutritionProfile profile, Update update, TelegramUser telegramUser) {
        CallbackQuery query = update.getCallbackQuery();
        ProfileEditAction action = extractActionOrNull(query.getData());

        //<handlerName><DELIM><actionCode><DELIM><mode><DELIM><payload>
        if (action == null) {
            log.error("Action in callback is null!");
            return;
        }
        actionHandlers.get(action).handle(
                Command.CALORIE_PROFILE_EDIT, calorieTelegramBot,
                profile, update, telegramUser);
    }

    public static ProfileEditAction extractActionOrNull(String data) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return ProfileEditAction.fromCode(parts[1]);
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractEditDataOrNull(String data) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return parts[2];
        } catch (Exception e) {
            return null;
        }
    }

    //Проверка новых данных (возвраст, пол)
    public static boolean checkNewData(String data) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return parts.length >= 3;
        } catch (Exception e) {
            return false;
        }
    }

    public InlineKeyboardMarkup buildKeyboard(UserNutritionProfile profile) {
        AppLanguage language = texts.languageOf(profile.getUserId());
        // Режим нормы — пара кнопок, текущий отмечен ✅ (CalorieNutritionProfileRecalculate)
        boolean manual = profile.isManualNorm();
        return TelegramKeyboard.builder().row(
                        buildActivityButton(ProfileEditAction.SEX, language),
                        buildActivityButton(ProfileEditAction.AGE, language)
                ).row(
                        buildActivityButton(ProfileEditAction.HEIGHT, language),
                        buildActivityButton(ProfileEditAction.CURRENT_WEIGHT, language)
                ).row(
                        buildActivityButton(ProfileEditAction.ACTIVITY, language),
                        buildActivityButton(ProfileEditAction.GOAL, language)
                ).row(
                        buildActivityButton(ProfileEditAction.DIET_TYPE, language)
                ).row(
                        buildActivityButton(ProfileEditAction.CALORIE_NORM, language),
                        buildActivityButton(ProfileEditAction.WATER_TARGET, language)
                ).row(
                        TelegramKeyboard.button(
                                (manual ? "" : "✅ ") + texts.get(language, BotTextKeys.PROFILE_EDIT_BUTTON_AUTO),
                                CalorieNutritionProfileRecalculate.callback(CalorieNutritionProfileRecalculate.AUTO)),
                        TelegramKeyboard.button(
                                (manual ? "✅ " : "") + texts.get(language, BotTextKeys.PROFILE_EDIT_BUTTON_MANUAL),
                                CalorieNutritionProfileRecalculate.callback(CalorieNutritionProfileRecalculate.MANUAL))
                ).row(
                        TelegramKeyboard.button(texts.get(language, BotTextKeys.COMMON_BUTTON_RETURN),
                                Command.CALORIE_PROFILE.getCommandText())
                )
                .build();

    }

    private InlineKeyboardButton buildActivityButton(ProfileEditAction action, AppLanguage language) {
        return TelegramKeyboard.button(
                texts.get(language, action.getTextKey()),
                getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + action.getCode()
        );
    }

    private String buildMessageText(UserNutritionProfile profile) {
        return profileTexts.buildProfileText(profile) + "\n\n"
                + texts.get(profile.getUserId(), BotTextKeys.PROFILE_EDIT_QUESTION);
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_PROFILE_EDIT.getCommandText();
    }

    public void register(ProfileEditAction profileAction, ProfileEditFieldHandler profileEditFieldHandler) {
        actionHandlers.put(profileAction, profileEditFieldHandler);
    }
}
