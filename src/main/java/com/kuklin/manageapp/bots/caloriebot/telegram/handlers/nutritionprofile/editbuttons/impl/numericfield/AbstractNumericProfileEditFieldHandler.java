package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.numericfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.InsufficientProfileDataException;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation.UserNutritionProfileValidationException;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.KeyboardTemplates;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditFieldHandler;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler.checkNewData;
import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler.extractActionOrNull;

/*
 * Абстрактный обработчик изменения полей, с целочисленными значениями
 *
 */
@Slf4j
public abstract class AbstractNumericProfileEditFieldHandler
        implements ProfileEditFieldHandler {

    //Команда для сохранения новых значений
    protected static final String SET_CMD = "SET";
    //Команда для продолжения работы со старым значением, без сохранения
    protected static final String ADJ_CMD = "ADJ";

    protected final UserNutritionProfileService profileService;
    protected final CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler;
    // Полем, а не в конструкторе: иначе пришлось бы менять конструкторы всех наследников
    @Autowired
    protected CalorieTexts texts;

    // ===== конфигурация =====
    protected final Integer defaultValue;
    protected final int min;
    protected final int max;
    // Шаги счётчика (положительные): кнопки ±small, ±mid, ±big
    protected final int small;
    protected final int mid;
    protected final int big;

    protected AbstractNumericProfileEditFieldHandler(
            UserNutritionProfileService profileService,
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler,
            Integer defaultValue,
            int min,
            int max,
            int small,
            int mid,
            int big
    ) {
        this.profileService = profileService;
        this.callbackHandler = callbackHandler;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.small = small;
        this.mid = mid;
        this.big = big;
    }

    @Override
    public void handle(
            Command command,
            TelegramBot telegramBot,
            UserNutritionProfile profile,
            Update update,
            TelegramUser telegramUser
    ) {
        CallbackQuery query = update.getCallbackQuery();
        Message message = TelegramBot.getCallbackMessage(query);

        Long chatId = message.getChatId();
        Integer messageId = message.getMessageId();
        //Извлечение данных из колбэка
        ProfileEditAction action = extractActionOrNull(query.getData());

        Integer currentValue = getCurrentValue(profile);

        //Проверяем есть ли новые данные для обработки
        //Если нет - отправляем начальное сообщение
        if (checkNewData(query.getData())) {
            Integer value = extractValueOrNull(query.getData());
            if (value == null) {
                log.error("Numeric value is NULL");
                return;
            }

            //Если нужно сохранить новые данные
            if (query.getData().contains(SET_CMD)) {
                profile = applyValue(profile, value);
                try {
                    //Обновляем данные
                    profile = patchProfile(profile);
                    //Пересчитываем нормы КБЖУ (своя норма калорий и воды при этом сохраняется — NormMode.MANUAL)
                    profileService.recalculateAndSave(profile);
                } catch (UserNutritionProfileValidationException e) {
                    //Недопустимые данные
                    telegramBot.sendReturnedMessage(chatId,
                            texts.get(telegramUser.getAppUserId(), BotTextKeys.PROFILE_VALIDATION_ERROR));
                } catch (InsufficientProfileDataException e) {
                    //Профиль ещё заполняется (диалог) — норму посчитаем, когда хватит данных
                }
                return;
            }
            currentValue = value;
        }

        InlineKeyboardMarkup keyboard = KeyboardTemplates.buildNumericKeyboard(
                command,
                action,
                currentValue,
                defaultValue,
                min,
                max,
                small,
                mid,
                big,
                value -> texts.get(telegramUser.getAppUserId(), action.getValueKey(), value),
                texts.get(telegramUser.getAppUserId(), BotTextKeys.COMMON_BUTTON_SAVE),
                texts.get(telegramUser.getAppUserId(), BotTextKeys.COMMON_BUTTON_BACK)
        );
        List<InlineKeyboardButton> extra = extraRow(profile);
        if (!extra.isEmpty()) {
            // Над «← Назад / Сохранить»
            keyboard.getKeyboard().add(keyboard.getKeyboard().size() - 1, extra);
        }

        telegramBot.sendEditMessage(chatId, buildText(profile), messageId, keyboard);
    }

    protected Integer extractValueOrNull(String data) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return Integer.parseInt(parts[3]);
        } catch (Exception e) {
            return null;
        }
    }

    /* ===== шаблонные методы ===== */

    protected abstract Integer getCurrentValue(UserNutritionProfile profile);
    //Изменение профиля, в соответствии с обработчиком
    protected abstract UserNutritionProfile applyValue(UserNutritionProfile profile, Integer value);
    protected UserNutritionProfile patchProfile(UserNutritionProfile profile) throws UserNutritionProfileValidationException {
        return profileService.patchProfile(profile);
    }

    protected abstract String buildText(UserNutritionProfile profile);

    /** Дополнительная строка кнопок под счётчиком (например, «↺ По формуле» у нормы воды). */
    protected List<InlineKeyboardButton> extraRow(UserNutritionProfile profile) {
        return List.of();
    }
}
