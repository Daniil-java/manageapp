package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.settings;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
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

/**
 * Обработчик, отвечает за настройку таймзоны
 */
@Component
@RequiredArgsConstructor
public class CalorieTimeZoneSettingsUpdateHandler implements CalorieSettingsHandler{
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserSettingsService userSettingsService;
    //Команды для коллбэка
    //SET - означает сохранение какого-либо параметра
    private static final String SET_CMD = "SET";
    //PAGE = перелистывание списка
    private static final String PAGE_CMD = "PAGE";
    private static final int PAGE_SIZE = 8;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        //Обрабатываем только колбэк
        if (!update.hasCallbackQuery()) return;

        CallbackQuery query = update.getCallbackQuery();
        Long chatId = query.getMessage().getChatId();
        String data = query.getData();

        //Если данные колбэка содержат только команду данного обработчика,
        //то отсылаем сообщение с начальной клавиатурой
        if (data.equals(getHandlerListName())) {
            calorieTelegramBot.sendEditMessage(
                    chatId,
                    getLabel(),
                    query.getMessage().getMessageId(),
                    getTimeZoneKeyboard(0)
            );
            return;
        }

        //Извлекаем нужные данные (команду) из данных колбэка
        String cmd = extractCmdOrNull(data);
        //Если команда SET
        if (SET_CMD.equals(cmd)) {
            //Извлекаем, выбранную из данных колбэка пользователем, таймзону
            String tz = extractTzOrNull(data);
            //Сохранение таймзоны
            userSettingsService.setTimeZoneOrNull(telegramUser.getTelegramId(), tz);

            //Возращаю сообщение с кнопкой возврата к настройкам
            calorieTelegramBot.sendEditMessage(
                    chatId,
                    "✅ Таймзона установлена: " + tz,
                    query.getMessage().getMessageId(),
                    TelegramKeyboard.builder()
                            .row(TelegramKeyboard.button(
                                    "\uD83D\uDD19 Вернуться к настройкам",
                                    Command.CALORIE_SETTINGS.getCommandText()
                            ))
                            .build()
            );
            return;
        }

        //Обработка команды PAGE
        if (PAGE_CMD.equals(cmd)) {
            //Ивзлекаем номер страницы
            Integer page = extractPageOrNull(data);
            if (page == null) return;

            calorieTelegramBot.sendEditMessage(
                    chatId,
                    getLabel(),
                    query.getMessage().getMessageId(),
                    getTimeZoneKeyboard(page)
            );
        }
    }

    private Integer extractPageOrNull(String data) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return Integer.parseInt(parts[2]);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractTzOrNull(String data) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return parts[2];
        } catch (Exception e) {
            return null;
        }
    }
    private String extractCmdOrNull(String data) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return parts[1];
        } catch (Exception e) {
            return null;
        }
    }

    //Клавиатура для выбора таймзоны
    private InlineKeyboardMarkup getTimeZoneKeyboard(int page) {
        int total = TIME_ZONES.size();
        int totalPages = (int) Math.ceil(total / (double) PAGE_SIZE);

        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, total);

        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        for (ZoneItem zone : TIME_ZONES.subList(from, to)) {
            builder.row(
                    TelegramKeyboard.button(
                            zone.label(),
                            getHandlerListName()
                                    + TelegramBot.DEFAULT_DELIMETER + SET_CMD
                                    + TelegramBot.DEFAULT_DELIMETER + zone.zoneId()
                    )
            );
        }

        String base = getHandlerListName()
                + TelegramBot.DEFAULT_DELIMETER + PAGE_CMD
                + TelegramBot.DEFAULT_DELIMETER;

        List<InlineKeyboardButton> nav = new ArrayList<>();

        if (page > 0)
            nav.add(TelegramKeyboard.button("⬅", base + (page - 1)));

        if (page < totalPages - 1)
            nav.add(TelegramKeyboard.button("➡", base + (page + 1)));

        if (!nav.isEmpty())
            builder.row(nav.toArray(new InlineKeyboardButton[0]));

        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_SETTINGS_TIMEZONE.getCommandText();
    }
    @Override
    public String getLabel() {
        return "\uD83C\uDF0EТаймзона";
    }

    private record ZoneItem(String label, String zoneId) {}
    private static final List<ZoneItem> TIME_ZONES = List.of(
            new ZoneItem("UTC−12", "Etc/GMT+12"),
            new ZoneItem("UTC−8 (LA)", "America/Los_Angeles"),
            new ZoneItem("UTC−5 (NY)", "America/New_York"),
            new ZoneItem("UTC−3 (SP)", "America/Sao_Paulo"),
            new ZoneItem("UTC+0 (London)", "Europe/London"),
            new ZoneItem("UTC+1 (Berlin)", "Europe/Berlin"),
            new ZoneItem("UTC+2 (Kyiv)", "Europe/Kyiv"),
            new ZoneItem("UTC+3 (MSK)", "Europe/Moscow"),
            new ZoneItem("UTC+4 (Dubai)", "Asia/Dubai"),
            new ZoneItem("UTC+5 (Tashkent)", "Asia/Tashkent"),
            new ZoneItem("UTC+5:30 (India)", "Asia/Kolkata"),
            new ZoneItem("UTC+7 (Bangkok)", "Asia/Bangkok"),
            new ZoneItem("UTC+8 (Singapore)", "Asia/Singapore"),
            new ZoneItem("UTC+9 (Tokyo)", "Asia/Tokyo"),
            new ZoneItem("UTC+10 (Sydney)", "Australia/Sydney")
    );
}
