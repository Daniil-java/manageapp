package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditFieldHandler;
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
                    .getOrCreateProfile(telegramUser.getTelegramId());
            handleAction(profile, update, telegramUser);
            if (update.getCallbackQuery().getData().contains(SET_CMD)) {
                sendMainMessage(update, telegramUser);
            }
        }
    }

    //Отправка главного сообщения редактирования
    public void sendMainMessage(Update update, TelegramUser telegramUser) {
        UserNutritionProfile profile = userNutritionProfileService
                .getOrCreateProfile(telegramUser.getTelegramId());
        Message message = update.getCallbackQuery().getMessage();

        calorieTelegramBot.sendEditMessage(
                message.getChatId(),
                buildMessageText(profile),
                message.getMessageId(),
                buildKeyboard()
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

    private InlineKeyboardMarkup buildKeyboard() {
        return TelegramKeyboard.builder().row(
                        buildActivityButton(ProfileEditAction.SEX),
                        buildActivityButton(ProfileEditAction.AGE)
                ).row(
                        buildActivityButton(ProfileEditAction.HEIGHT),
                        buildActivityButton(ProfileEditAction.CURRENT_WEIGHT)
                ).row(
                        buildActivityButton(ProfileEditAction.ACTIVITY),
                        buildActivityButton(ProfileEditAction.GOAL)
                ).row(
                        buildActivityButton(ProfileEditAction.DIET_TYPE)
                ).row(
                        buildActivityButton(ProfileEditAction.CALORIE_NORM),
                        buildActivityButton(ProfileEditAction.WATER_TARGET)
                ).row(
                        TelegramKeyboard.button("Вернуться", Command.CALORIE_PROFILE.getCommandText())
                )
                .build();

    }

    private InlineKeyboardButton buildActivityButton(ProfileEditAction action) {
        return TelegramKeyboard.button(
                action.getLabel(),
                getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + action.getCode()
        );
    }

    private String buildMessageText(UserNutritionProfile profile) {
        return profile.toTelegramView() + "\n\nЧто хотите изменить?";
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_PROFILE_EDIT.getCommandText();
    }

    public void register(ProfileEditAction profileAction, ProfileEditFieldHandler profileEditFieldHandler) {
        actionHandlers.put(profileAction, profileEditFieldHandler);
    }
}
