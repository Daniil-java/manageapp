package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.InsufficientProfileDataException;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation.UserNutritionProfileValidationException;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditFieldHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler.checkNewData;
import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler.extractActionOrNull;

public abstract class AbstractProfileEditFieldHandler<T>
        implements ProfileEditFieldHandler {

    protected static final String VALIDATION_ERROR = "Некорректные данные!";

    protected final UserNutritionProfileService profileService;
    protected final CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler;

    protected AbstractProfileEditFieldHandler(
            UserNutritionProfileService profileService,
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler
    ) {
        this.profileService = profileService;
        this.callbackHandler = callbackHandler;
    }

    @Override
    public final void handle(
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
        ProfileEditAction action = extractActionOrNull(query.getData());

        T currentValue = getCurrentValue(profile);

        if (checkNewData(query.getData())) {
            T value = extractValue(query.getData());
            if (value == null) {
                onInvalidValue();
                return;
            }

            if (shouldSave(query.getData())) {
                try {
                    profile = applyValue(profile, value);
                    patchProfile(profile);
                    profileService.recalculateAndSave(profile);
                } catch (UserNutritionProfileValidationException e) {
                    telegramBot.sendReturnedMessage(chatId, VALIDATION_ERROR);
                } catch (InsufficientProfileDataException ignored) {
                }
                return;
            }

            currentValue = value;
        }

        telegramBot.sendEditMessage(
                chatId,
                buildText(profile),
                messageId,
                buildKeyboard(command, action, currentValue)
        );
    }

    /* ===== hooks ===== */

    protected boolean shouldSave(String data) {
        return data.contains("SET");
    }

    protected void onInvalidValue() {
        // по умолчанию ничего
    }

    protected UserNutritionProfile patchProfile(UserNutritionProfile profile)
            throws UserNutritionProfileValidationException {
        return profileService.patchProfile(profile);
    }

    /* ===== шаблонные методы ===== */

    protected abstract T extractValue(String data);

    protected abstract T getCurrentValue(UserNutritionProfile profile);

    protected abstract UserNutritionProfile applyValue(UserNutritionProfile profile, T value);

    protected abstract String buildText(UserNutritionProfile profile);

    protected abstract InlineKeyboardMarkup buildKeyboard(
            Command command,
            ProfileEditAction action,
            T currentValue
    );
}
