package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.numericfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.InsufficientProfileDataException;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation.UserNutritionProfileValidationException;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.KeyboardTemplates;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditFieldHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler.checkNewData;
import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler.extractActionOrNull;
import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.numericfield.AbstractDecimalProfileEditFieldHandler.VALIDATION_ERROR;

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

    // ===== конфигурация =====
    protected final Integer defaultValue;
    protected final int min;
    protected final int max;
    protected final int bigNeg;
    protected final int neg;
    protected final int pos;
    protected final int bigPos;

    protected AbstractNumericProfileEditFieldHandler(
            UserNutritionProfileService profileService,
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler,
            Integer defaultValue,
            int min,
            int max,
            int bigNeg,
            int neg,
            int pos,
            int bigPos
    ) {
        this.profileService = profileService;
        this.callbackHandler = callbackHandler;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.bigNeg = bigNeg;
        this.neg = neg;
        this.pos = pos;
        this.bigPos = bigPos;
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
        Message message = query.getMessage();

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
                    //Пересчитываем нормы КБЖУ
                    profileService.recalculateAndSave(profile);
                } catch (UserNutritionProfileValidationException e) {
                    //Недопустимые данные
                    telegramBot.sendReturnedMessage(chatId, VALIDATION_ERROR);
                } catch (InsufficientProfileDataException e) {
                    return;
                }
                return;
            }
            currentValue = value;
        }

        telegramBot.sendEditMessage(
                chatId,
                buildText(profile),
                messageId,
                KeyboardTemplates.buildNumericKeyboard(
                        command,
                        action,
                        currentValue,
                        defaultValue,
                        min,
                        max,
                        bigNeg,
                        neg,
                        pos,
                        bigPos,
                        action.getLabelFormat()
                )
        );
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
        return profileService.patchProfile(
                profile.getUserId(),
                profile.getSex(),
                profile.getAgeYears(),
                profile.getHeightCm(),
                profile.getCurrentWeightKg(),
                profile.getActivityLevel(),
                profile.getGoal(),
                profile.getWaterTargetMlPerDay(),
                profile.getDietType()
        );
    }

    protected abstract String buildText(UserNutritionProfile profile);
}
