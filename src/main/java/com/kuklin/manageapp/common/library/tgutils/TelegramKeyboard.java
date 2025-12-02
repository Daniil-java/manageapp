package com.kuklin.manageapp.common.library.tgutils;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

public class TelegramKeyboard {
    //Разделитель
    public static final String DELIMETER = "#cal#";
    //CallbackData для игнорируемой кнопки
    public static final String IGNORE = "$ignore$";
    //CallbackData для деактивированной кнопки
    public static final String DISABLED_DATA = "$disable$";
    //Текст для деактивированной кнопки
    public static final String DISABLED_TEXT = "❌";
    //Текст для пустой кнопки
    private static final String EMPTY =" ";
    //Выбор даты
    private static final String DATE ="$date$";

    public static class TelegramKeyboardBuilder {
        private List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        public TelegramKeyboardBuilder row(InlineKeyboardButton... buttons) {
            keyboard.add(Arrays.asList(buttons));
            return this;
        }

        // Добавляем уже готовую строку
        public TelegramKeyboardBuilder row(List<InlineKeyboardButton> row) {
            keyboard.add(row);
            return this;
        }

        // Добавляем несколько строк сразу
        public TelegramKeyboardBuilder rows(List<InlineKeyboardButton>... rows) {
            keyboard.addAll(Arrays.asList(rows));
            return this;
        }

        public TelegramKeyboardBuilder row(String... args) {
            if (args.length % 2 != 0) {
                throw new IllegalArgumentException("Нужно передавать пары: text, callbackData");
            }
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (int i = 0; i < args.length; i += 2) {
                row.add(button(args[i], args[i + 1]));
            }
            keyboard.add(row);
            return this;
        }

        // Возвращаем итоговую клавиатуру
        public InlineKeyboardMarkup build() {
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(keyboard);
            return markup;
        }

    }

    // Удобный статический метод для старта билдера
    public static TelegramKeyboardBuilder builder() {
        return new TelegramKeyboardBuilder();
    }

    public static InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    /*
        Настройка и создание календаря
        В параметры передаются месяц и год, для навигации по календарю.
        А также, комманды:
        prevCommand - для перелистывания календаря назад
        nextCommand - для перелистывания календаря вперед
        chooseCommand - для выбора конкретного дня
        backCommand - для возвращения к прошлому состоянию сообщения, до календаря

        Set<LocalDate> - даты, которые должны быть недоступны в календаре
         */
    public static InlineKeyboardMarkup getDefaultCalendarBeforeToday(
            int year, int month,
            String prevCommand, String nextCommand, String chooseCommand,
            String backCommand,
            Set<LocalDate> disabledDates
    ) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate firstDay = ym.atDay(1);
        DayOfWeek firstDow = firstDay.getDayOfWeek();

        LocalDate today = LocalDate.now();

        TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        // Заголовок (только месяц и год)
        builder.row(
                button(ym.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("ru")).toUpperCase() + " " + year, IGNORE)
        );

        // Дни недели
        builder.row(
                Arrays.stream(DayOfWeek.values())
                        .map(dow -> button(dow.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("ru")).toUpperCase(), IGNORE))
                        .toList()
        );

        // Сетка дней
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        int shift = (firstDow.getValue() + 6) % 7; // нормализация: Пн=0
        for (int i = 0; i < shift; i++) {
            currentRow.add(button(EMPTY, IGNORE));
        }

        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate date = ym.atDay(day);

            if ((disabledDates != null && disabledDates.contains(date)) || date.isBefore(today)) {
                currentRow.add(button(DISABLED_TEXT + day, DISABLED_DATA));
            } else {
                currentRow.add(button(String.valueOf(day),
                        chooseCommand + DATE + DELIMETER + year + DELIMETER + month + DELIMETER + day));
            }

            if (currentRow.size() == 7) {
                builder.row(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            while (currentRow.size() < 7) currentRow.add(button(EMPTY, IGNORE));
            builder.row(currentRow);
        }

        // Навигация (внизу)
        boolean isCurrentMonth = (year == today.getYear() && month <= today.getMonthValue());
        builder.row(
                isCurrentMonth
                        ? button(" ", IGNORE)
                        : button("<", prevCommand + DELIMETER + year + DELIMETER + (month - 1)),
                button(">", nextCommand + DELIMETER + year + DELIMETER + (month + 1))
        );

        // Кнопка "Назад" (самая нижняя строка)
        builder.row(
                button("Назад", backCommand)
        );

        return builder.build();
    }

    public static InlineKeyboardMarkup getSingleButtonKeyboard(String text, String data) {
        return builder().row(button(text, data)).build();
    }

}
