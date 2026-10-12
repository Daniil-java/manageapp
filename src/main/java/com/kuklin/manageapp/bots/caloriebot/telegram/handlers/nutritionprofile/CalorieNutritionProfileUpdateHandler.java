package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.texts.NutritionProfileTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/*
* Обработчик команды профиля
*
* Отвечает за:
* - Возврат сообщения с профилем
* - Возвращает ответ на сообщение и колбэк
 */
@Component
@RequiredArgsConstructor
public class CalorieNutritionProfileUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserNutritionProfileService userNutritionProfileService;
    private final NutritionProfileTexts profileTexts;
    private final CalorieTexts texts;
    private static final String EDIT_HANDLER_CMD =
            Command.CALORIE_PROFILE_EDIT.getCommandText();

    //Комманда-ссылка на начало диалога заполнения профиля
    private static final String DIALOGUE_HANDLER_CMD =
            Command.CALORIE_PROFILE_DIALOGUE.getCommandText();

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long appUserId = telegramUser.getAppUserId();
        //Получение профиля пользователя
        UserNutritionProfile profile = userNutritionProfileService
                .getOrCreateProfile(appUserId);

        InlineKeyboardMarkup keyboardMarkup;
        String text;

        //Если не достаточно параметров - отправляем ссылку на диалог
        //Если достаточно - возвращаем профиль с кнопками
        if (profile.checkTargetCalculateParams()) {
            keyboardMarkup = getProfileSettingsKeyboard(appUserId);
            text = profileTexts.buildProfileText(profile);
        } else {
            text = texts.get(appUserId, BotTextKeys.PROFILE_EMPTY);
            keyboardMarkup = getStartProfileKeyboard(appUserId);
        }

        //Если пришло сообщение - возвращаем новое сообщение
        if (update.hasMessage()) {
            calorieTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    text,
                    keyboardMarkup,
                    null
            );
            // Если колбэк - редактируем существующее
        } else if (update.hasCallbackQuery()) {
            calorieTelegramBot.sendEditMessage(
                    update.getCallbackQuery().getMessage().getChatId(),
                    text,
                    update.getCallbackQuery().getMessage().getMessageId(),
                    keyboardMarkup
            );
        }
    }

    //Клавиатура для старта диалога
    private InlineKeyboardMarkup getStartProfileKeyboard(Long appUserId) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        builder.row(
                TelegramKeyboard.button(
                        texts.get(appUserId, BotTextKeys.PROFILE_BUTTON_FILL), DIALOGUE_HANDLER_CMD
                )
        );

        return builder.build();
    }

    private InlineKeyboardMarkup getProfileSettingsKeyboard(Long appUserId) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        builder.row(
                TelegramKeyboard.button(
                        texts.get(appUserId, BotTextKeys.PROFILE_BUTTON_EDIT), EDIT_HANDLER_CMD
                )
        ).row(
                TelegramKeyboard.button(
                        texts.get(appUserId, BotTextKeys.PROFILE_BUTTON_FILL), DIALOGUE_HANDLER_CMD
                )
        )
        ;

        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_PROFILE.getCommandText();
    }
}
