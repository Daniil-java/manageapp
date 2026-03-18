package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.startflow;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.welcome.WelcomeCalorieUpdateHandler.INSTR_URL;

@Component
@RequiredArgsConstructor
public class StartFlowCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserNutritionProfileService userNutritionProfileService;
    private static final String MSG =
            """
            Привет! 👋 Я — твой умный помощник для учета калорий и дневник питания.
                        
            Шаг 1. Заполни профиль
            Пожалуйста, нажми на кнопку «Заполнить профиль» под этим сообщением. Это нужно, чтобы я рассчитал твою личную суточную норму. Как только ты это сделаешь, у тебя появятся красивые и удобные шкалы прогресса по калориям и БЖУ вместо скучных цифр! 📊
                        
            Как записывать еду? Это очень просто:
            Текстом 📝: Просто напиши в чат (например: «два вареных яйца, тост и кофе с молоком»).
            Голосом 🎙: Лень печатать? Наговори съеденное в голосовом сообщении — я сам всё расшифрую и посчитаю.
            По фото 📸: Отправь фотографию своей тарелки, а мой ИИ распознает блюдо и оценит порцию.
                        
            Полезные команды, которые всегда под рукой:
            /menu — вызов главного меню с кнопками прямо в чате.
            /keyboard — вернуть меню-клавиатуру в самый низ экрана, если она пропала.
            /support — написать в нашу поддержку (если есть вопросы или что-то пошло не так).
                    """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;
        boolean isFilledProfile = userNutritionProfileService
                .getOrCreateProfile(telegramUser.getTelegramId())
                .checkTargetCalculateParams();

        Integer lastMsgId = update.getCallbackQuery().getMessage().getMessageId();
        calorieTelegramBot.sendEditMessage(
                update.getCallbackQuery().getMessage().getChatId(),
                MSG,
                lastMsgId,
                null
        );

        calorieTelegramBot.sendReturnedMessage(
                update.getCallbackQuery().getMessage().getChatId(),
                MSG,
                getStartFlowKeyboard(isFilledProfile),
                null
        );
    }

    private InlineKeyboardMarkup getStartFlowKeyboard(boolean isFilledProfile) {
        TelegramKeyboard.TelegramKeyboardBuilder keyboard = new TelegramKeyboard.TelegramKeyboardBuilder();

        InlineKeyboardButton profileButton = TelegramKeyboard.button("Заполнить профиль", Command.CALORIE_PROFILE.getCommandText());

        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("📖 Инструкция в miniApp");
        button.setWebApp(new WebAppInfo(INSTR_URL));

        keyboard.row(TelegramKeyboard.button("Далее", Command.CALORIE_MENU.getCommandText()));
        if (!isFilledProfile) {
            keyboard.row(profileButton);
        }

        return keyboard.row(button)
                .row(TelegramKeyboard.button("Закрыть", Command.CALORIE_CLOSE.getCommandText()))
                .build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_START_FLOW_1.getCommandText();
    }
}
