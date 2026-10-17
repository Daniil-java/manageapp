package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

//Класс с шаблонными клавиатурами
public class KeyboardTemplates {
    public static final String SET_CMD = "SET";
    public static final String ADJ_CMD = "ADJ";

    //Labeled Enum-клавиатурура
    public static InlineKeyboardMarkup buildEnumKeyboard(
            Command command,
            ProfileEditAction action,
            Collection<? extends UserNutritionProfile.Labeled> values,
            int perRow
    ) {
        if (values == null || values.isEmpty()) {
            return TelegramKeyboard.builder().build();
        }
        //Валидация допустипых значений количества элементов в ряду
        if (perRow <= 0) perRow = 2;

        String callbackBase = command.getCommandText()
                + TelegramBot.DEFAULT_DELIMETER + action.getCode()
                + TelegramBot.DEFAULT_DELIMETER;

        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        List<InlineKeyboardButton> currentRow = new ArrayList<>(perRow);
        int i = 0;
        for (UserNutritionProfile.Labeled labeled : values) {
            String payload;
            if (labeled instanceof Enum<?>) {
                Enum<?> en = (Enum<?>) labeled;
                payload = en.name();
            } else {
                // fallback: если вдруг не enum — используем label как payload (или пропускаем)
                payload = labeled.getLabel();
            }
            String label = labeled.getLabel();
            String callback = callbackBase + payload + TelegramBot.DEFAULT_DELIMETER + SET_CMD;  // используем name() как payload
                                                                // SET комманда, чтобы знать что в этом колбэке сразу идет установка параметра

            currentRow.add(TelegramKeyboard.button(label, callback));
            i++;

            if (i % perRow == 0) {
                builder.row(currentRow.toArray(new InlineKeyboardButton[0]));
                currentRow.clear();
            }
        }
        if (!currentRow.isEmpty()) {
            builder.row(currentRow.toArray(new InlineKeyboardButton[0]));
        }

        return builder.build();
    }

    //Клавиатура-счетчик для целочисленных значений
    public static InlineKeyboardMarkup buildNumericKeyboard(
            Command command,
            ProfileEditAction action,
            Integer currentValue,
            Integer defValue,
            Integer min,
            Integer max,
            Integer bigNeg,
            Integer neg,
            Integer pos,
            Integer bigPos,
            String labelFormat // <-- новый параметр
    ) {

        if (currentValue == null) currentValue = defValue;

        String callbackBase = command.getCommandText()
                + TelegramBot.DEFAULT_DELIMETER + action.getCode()
                + TelegramBot.DEFAULT_DELIMETER;

        List<InlineKeyboardButton> valueRow = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        List<InlineKeyboardButton> row2 = new ArrayList<>();

        // 🔹 Отдельная строка с текущим значением
        valueRow.add(
                TelegramKeyboard.button(
                        String.format(labelFormat, currentValue),
                        "noop" // заглушка, чтобы не реагировала
                )
        );

        if (currentValue - bigNeg >= min) {
            row.add(TelegramKeyboard.button(
                    bigNeg.toString(),
                    callbackBase + ADJ_CMD + TelegramBot.DEFAULT_DELIMETER + (currentValue + bigNeg)
            ));
        }

        if (currentValue - neg >= min) {
            row.add(TelegramKeyboard.button(
                    neg.toString(),
                    callbackBase + ADJ_CMD + TelegramBot.DEFAULT_DELIMETER + (currentValue + neg)
            ));
        }

        if (currentValue + pos <= max) {
            row.add(TelegramKeyboard.button(
                    "+" + pos,
                    callbackBase + ADJ_CMD + TelegramBot.DEFAULT_DELIMETER + (currentValue + pos)
            ));
        }

        if (currentValue + bigPos <= max) {
            row.add(TelegramKeyboard.button(
                    "+" + bigPos,
                    callbackBase + ADJ_CMD + TelegramBot.DEFAULT_DELIMETER + (currentValue + bigPos)
            ));
        }

        row2.add(TelegramKeyboard.button(
                "Сохранить",
                callbackBase + SET_CMD + TelegramBot.DEFAULT_DELIMETER + currentValue
        ));

        return TelegramKeyboard.builder()
                .row(valueRow.toArray(new InlineKeyboardButton[0])) // 🔹 строка значения
                .row(row.toArray(new InlineKeyboardButton[0]))
                .row(row2.toArray(new InlineKeyboardButton[0]))
                .build();
    }

    //Клавиатура-счетчик для значений с плавающей запятой
    public static InlineKeyboardMarkup buildDecimalKeyboard(
            Command command,
            ProfileEditAction action,
            BigDecimal currentValue,
            BigDecimal def,
            BigDecimal min,
            BigDecimal max,
            BigDecimal bigNeg,
            BigDecimal neg,
            BigDecimal pos,
            BigDecimal bigPos,
            String labelFormat // новый параметр
    ) {

        if (currentValue == null) currentValue = def;

        String callbackBase = command.getCommandText()
                + TelegramBot.DEFAULT_DELIMETER + action.getCode()
                + TelegramBot.DEFAULT_DELIMETER;

        List<InlineKeyboardButton> valueRow = new ArrayList<>();
        List<InlineKeyboardButton> adjustRow = new ArrayList<>();
        List<InlineKeyboardButton> saveRow = new ArrayList<>();

        // ===== Строка с текущим значением =====
        valueRow.add(
                TelegramKeyboard.button(
                        String.format(labelFormat, currentValue.toPlainString()),
                        "noop"
                )
        );

        // ===== Кнопки уменьшения =====
        BigDecimal newValue;

        newValue = currentValue.add(bigNeg);
        if (newValue.compareTo(min) >= 0) {
            adjustRow.add(
                    TelegramKeyboard.button(
                            bigNeg.toPlainString(),
                            callbackBase + ADJ_CMD + TelegramBot.DEFAULT_DELIMETER + newValue
                    )
            );
        }

        newValue = currentValue.add(neg);
        if (newValue.compareTo(min) >= 0) {
            adjustRow.add(
                    TelegramKeyboard.button(
                            neg.toPlainString(),
                            callbackBase + ADJ_CMD + TelegramBot.DEFAULT_DELIMETER + newValue
                    )
            );
        }

        // ===== Кнопки увеличения =====
        newValue = currentValue.add(pos);
        if (newValue.compareTo(max) <= 0) {
            adjustRow.add(
                    TelegramKeyboard.button(
                            "+" + pos.toPlainString(),
                            callbackBase + ADJ_CMD + TelegramBot.DEFAULT_DELIMETER + newValue
                    )
            );
        }

        newValue = currentValue.add(bigPos);
        if (newValue.compareTo(max) <= 0) {
            adjustRow.add(
                    TelegramKeyboard.button(
                            "+" + bigPos.toPlainString(),
                            callbackBase + ADJ_CMD + TelegramBot.DEFAULT_DELIMETER + newValue
                    )
            );
        }

        // ===== Кнопка сохранения =====
        saveRow.add(
                TelegramKeyboard.button(
                        "Сохранить",
                        callbackBase + SET_CMD + TelegramBot.DEFAULT_DELIMETER + currentValue
                )
        );

        return TelegramKeyboard.builder()
                .row(valueRow.toArray(new InlineKeyboardButton[0]))
                .row(adjustRow.toArray(new InlineKeyboardButton[0]))
                .row(saveRow.toArray(new InlineKeyboardButton[0]))
                .build();
    }


}
