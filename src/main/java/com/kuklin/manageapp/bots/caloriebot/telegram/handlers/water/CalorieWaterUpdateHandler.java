package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.water;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.WaterEntry;
import com.kuklin.manageapp.bots.caloriebot.services.AnalyticsService;
import com.kuklin.manageapp.bots.caloriebot.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.services.WaterEntryService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.KeyboardTemplates.ADJ_CMD;

@Component
@RequiredArgsConstructor
public class CalorieWaterUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final AnalyticsService analyticsService;
    private final WaterEntryService waterEntryService;
    private final UserNutritionProfileService userNutritionProfileService;

    private static final Command CMD = Command.CALORIE_WATER;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasMessage()) {
            processMessage(update, telegramUser);
        } if (update.hasCallbackQuery()) {
            processCallback(update, telegramUser);
        }
    }

    private void processCallback(Update update, TelegramUser telegramUser) {
        CallbackQuery query = update.getCallbackQuery();
        String data = query.getData();
        String cmd = extractCommandOrNull(data);

        if (ADJ_CMD.equals(cmd)) {
            // Логика изменения данных остается только здесь
            Integer waterMl = extractWaterValueOrNull(data);
            waterEntryService.addWater(telegramUser.getTelegramId(), waterMl);

            // Перерисовываем интерфейс
            refreshWaterInterface(
                    telegramUser,
                    query.getMessage().getChatId(),
                    query.getMessage().getMessageId()
            );
        }
    }

    private void processMessage(Update update, TelegramUser telegramUser) {
        refreshWaterInterface(telegramUser, update.getMessage().getChatId(), null);
    }

    private void refreshWaterInterface(TelegramUser telegramUser, Long chatId, Integer messageId) {
        // 1. Общая логика получения данных
        UserNutritionProfile profile = userNutritionProfileService
                .getOrCreateProfile(telegramUser.getTelegramId());
        Integer currentWater = analyticsService.getTodayWaterMl(telegramUser.getTelegramId());

        InlineKeyboardMarkup markup = buildNumericKeyboard(
                currentWater, 0,
                -500, -100, 100, 500
        );

        String statusText = WaterEntry.getWaterStatusText(
                currentWater, profile.getWaterTargetMlPerDay());

        // 2. Выбор метода отправки (новое сообщение или редактирование старого)
        if (messageId != null) {
            calorieTelegramBot.sendEditMessage(chatId, statusText, messageId, markup);
        } else {
            calorieTelegramBot.sendReturnedMessage(chatId, statusText, markup, null);
        }
    }

    private Integer extractWaterValueOrNull(String data) {
        //<hndlcmd><adj|set><value>
        try {
            String parts[] = data.split(TelegramBot.DEFAULT_DELIMETER);
            return Integer.valueOf(parts[2]);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractCommandOrNull(String data) {
        //<hndlcmd><adj|set><value>
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            if (parts[1].equals(ADJ_CMD)) {
                return parts[1];
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static InlineKeyboardMarkup buildNumericKeyboard(
            Integer currentWater, //Изменяемое число внутри счетчика
            Integer min,
            Integer bigNeg, Integer neg, Integer pos, Integer bigPos
    ) {

        String callbackBase = CMD.getCommandText()
                + TelegramBot.DEFAULT_DELIMETER;

        // Собираем кнопки динамически
        List<InlineKeyboardButton> row0 = new ArrayList<>();
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        List<InlineKeyboardButton> row2 = new ArrayList<>();

        if (currentWater - bigNeg >= min) {
            row1.add(TelegramKeyboard.button(bigNeg.toString(), callbackBase + ADJ_CMD + TelegramBot.DEFAULT_DELIMETER + (bigNeg)));
        }
        if (currentWater - neg >= min) {
            row1.add(TelegramKeyboard.button(neg.toString(), callbackBase + ADJ_CMD + TelegramBot.DEFAULT_DELIMETER + (neg)));
        }

        row1.add(TelegramKeyboard.button("+" + pos.toString(), callbackBase + ADJ_CMD + TelegramBot.DEFAULT_DELIMETER + (pos)));
        row1.add(TelegramKeyboard.button("+" + bigPos.toString(), callbackBase + ADJ_CMD + TelegramBot.DEFAULT_DELIMETER + (bigPos)));

        row2.add(TelegramKeyboard.button("Закрыть", Command.CALORIE_CLOSE.getCommandText()));

//        <handlecmd><action><set|adj><value>

        return TelegramKeyboard.builder()
                .row(row0)
                .row(row1.toArray(new InlineKeyboardButton[0]))
                .row(row2)
                .build();
    }

    @Override
    public String getHandlerListName() {
        return CMD.getCommandText();
    }
}
