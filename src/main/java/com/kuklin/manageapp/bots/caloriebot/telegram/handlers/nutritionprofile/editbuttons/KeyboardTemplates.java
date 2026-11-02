package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.ButtonStyle;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

//Класс с шаблонными клавиатурами
public class KeyboardTemplates {
    public static final String SET_CMD = "SET";
    public static final String ADJ_CMD = "ADJ";
    // Кнопка-подпись (текущее значение) и шаг, упёршийся в границу, — ничего не делают
    private static final String NO_ACTION = TelegramKeyboard.NO_ACTION;

    /**
     * «← Назад» без сохранения: в редакторе профиля — к списку полей,
     * в пошаговом заполнении (диалоге) — к экрану профиля, уже сохранённые шаги остаются.
     */
    public static String getBackData(Command command) {
        return command == Command.CALORIE_PROFILE_DIALOGUE
                ? Command.CALORIE_PROFILE.getCommandText()
                : command.getCommandText();
    }

    /**
     * Labeled Enum-клавиатура: варианты по perRow в ряд, у текущего значения ✅, внизу «← Назад».
     * labelOf — подпись значения на языке пользователя; current — текущее значение или null.
     */
    public static InlineKeyboardMarkup buildEnumKeyboard(
            Command command,
            ProfileEditAction action,
            Collection<? extends UserNutritionProfile.Labeled> values,
            int perRow,
            Function<UserNutritionProfile.Labeled, String> labelOf,
            UserNutritionProfile.Labeled current,
            String backLabel
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
                // fallback: если вдруг не enum — используем ключ текста как payload
                payload = labeled.getTextKey();
            }
            String label = (labeled.equals(current) ? "✅ " : "") + labelOf.apply(labeled);
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
        builder.row(buildBackButton(command, backLabel));

        return builder.build();
    }

    /**
     * Клавиатура-счетчик для целочисленных значений:
     * [ Ваш рост: 175 см ]
     * [ −10 ] [ −5 ] [ −1 ]
     * [ +1 ] [ +5 ] [ +10 ]
     * [ ← Назад ] [ ✅ Сохранить ]
     * Шаг за границу ставит границу; на самой границе шаги в её сторону ничего не делают — ряды не сдвигаются.
     */
    public static InlineKeyboardMarkup buildNumericKeyboard(
            Command command,
            ProfileEditAction action,
            Integer currentValue,
            Integer defValue,
            int min,
            int max,
            int small,
            int mid,
            int big,
            Function<String, String> valueLabel, // подпись текущего значения: «Ваш рост: 175 см»
            String saveLabel,
            String backLabel
    ) {
        int current = currentValue == null ? defValue : currentValue;
        String callbackBase = command.getCommandText()
                + TelegramBot.DEFAULT_DELIMETER + action.getCode()
                + TelegramBot.DEFAULT_DELIMETER;

        // long — без переполнения у границ int (у норм калорий и воды max = Integer.MAX_VALUE)
        Function<Integer, InlineKeyboardButton> step = delta -> {
            int target = (int) Math.max(min, Math.min(max, (long) current + delta));
            String label = (delta < 0 ? "−" : "+") + Math.abs(delta);
            return buildStepButton(label, target == current, callbackBase, String.valueOf(target));
        };

        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(valueLabel.apply(String.valueOf(current)), NO_ACTION))
                .row(step.apply(-big), step.apply(-mid), step.apply(-small))
                .row(step.apply(small), step.apply(mid), step.apply(big))
                .row(
                        buildBackButton(command, backLabel),
                        buildSaveButton(saveLabel, callbackBase, String.valueOf(current))
                )
                .build();
    }

    /** То же для значений с плавающей запятой (вес). Шаги — со scale значения («1.0»), подпись без нулей («1»). */
    public static InlineKeyboardMarkup buildDecimalKeyboard(
            Command command,
            ProfileEditAction action,
            BigDecimal currentValue,
            BigDecimal def,
            BigDecimal min,
            BigDecimal max,
            BigDecimal small,
            BigDecimal mid,
            BigDecimal big,
            Function<String, String> valueLabel, // подпись текущего значения: «Ваш вес: 70.5 кг»
            String saveLabel,
            String backLabel
    ) {
        BigDecimal current = currentValue == null ? def : currentValue;
        String callbackBase = command.getCommandText()
                + TelegramBot.DEFAULT_DELIMETER + action.getCode()
                + TelegramBot.DEFAULT_DELIMETER;

        Function<BigDecimal, InlineKeyboardButton> step = delta -> {
            BigDecimal target = current.add(delta).max(min).min(max);
            String label = (delta.signum() < 0 ? "−" : "+") + delta.abs().stripTrailingZeros().toPlainString();
            return buildStepButton(label, target.compareTo(current) == 0, callbackBase, target.toPlainString());
        };

        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(valueLabel.apply(current.toPlainString()), NO_ACTION))
                .row(step.apply(big.negate()), step.apply(mid.negate()), step.apply(small.negate()))
                .row(step.apply(small), step.apply(mid), step.apply(big))
                .row(
                        buildBackButton(command, backLabel),
                        buildSaveButton(saveLabel, callbackBase, current.toPlainString())
                )
                .build();
    }

    private static InlineKeyboardButton buildStepButton(String label, boolean atBound, String callbackBase, String target) {
        return TelegramKeyboard.button(label,
                atBound ? NO_ACTION : callbackBase + ADJ_CMD + TelegramBot.DEFAULT_DELIMETER + target);
    }

    private static InlineKeyboardButton buildSaveButton(String saveLabel, String callbackBase, String value) {
        return TelegramKeyboard.button("✅ " + saveLabel, callbackBase + SET_CMD + TelegramBot.DEFAULT_DELIMETER + value,
                ButtonStyle.SUCCESS);
    }

    private static InlineKeyboardButton buildBackButton(Command command, String backLabel) {
        return TelegramKeyboard.button(backLabel, getBackData(command));
    }
}
