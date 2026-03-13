package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.common.CalorieBotUpdateHandler;
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
    private static final String EDIT_HANDLER_CMD =
            Command.CALORIE_PROFILE_EDIT.getCommandText();

    //Комманда-ссылка на начало диалога заполнения профиля
    private static final String DIALOGUE_HANDLER_CMD =
            Command.CALORIE_PROFILE_DIALOGUE.getCommandText();
    private static final String START_TEXT =
            """
                    Твой профиль питания ещё не заполнен 👇
                                        
                    Чтобы рассчитать суточную норму калорий и БЖУ,
                    мне нужны несколько параметров.
                    """;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        //Получение профиля пользователя
        UserNutritionProfile profile = userNutritionProfileService
                .getOrCreateProfile(telegramUser.getTelegramId());

        InlineKeyboardMarkup keyboardMarkup;
        String text;

        //Если не достаточно параметров - отправляем ссылку на диалог
        //Если достаточно - возвращаем профиль с кнопками
        if (profile.checkTargetCalculateParams()) {
            keyboardMarkup = getProfileSettingsKeyboard();
            text = profile.toTelegramView();
        } else {
            text = START_TEXT;
            keyboardMarkup = getStartProfileKeyboard();
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
    public static InlineKeyboardMarkup getStartProfileKeyboard() {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        builder.row(
                TelegramKeyboard.button(
                        "\uD83D\uDD04 Заполнить профиль", DIALOGUE_HANDLER_CMD
                )
        );

        return builder.build();
    }
    public static InlineKeyboardMarkup getProfileSettingsKeyboard() {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        builder.row(
                TelegramKeyboard.button(
                        "✏️ Изменить профиль", EDIT_HANDLER_CMD
                )
        ).row(
                TelegramKeyboard.button(
                        "\uD83D\uDD04 Заполнить профиль", DIALOGUE_HANDLER_CMD
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
