package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.InsufficientProfileDataException;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation.UserNutritionProfileValidationException;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
@Slf4j
public class CalorieNutritionProfileRecalculate implements CalorieBotUpdateHandler {
    private final UserNutritionProfileService userNutritionProfileService;
    private final CalorieNutritionProfileEditCallbackUpdateHandler calorieNutritionProfileEditCallbackUpdateHandler;
    private final CalorieTelegramBot calorieTelegramBot;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(telegramUser.getAppUserId());
        UserNutritionProfile oldProfile = profile.copy();
        try {
            profile = userNutritionProfileService.recalculateAndSave(profile);
            calorieTelegramBot.sendEditMessage(
                    update.getCallbackQuery().getMessage().getChatId(),
                    profile.toTelegramDiffView(oldProfile),
                    update.getCallbackQuery().getMessage().getMessageId(),
                    calorieNutritionProfileEditCallbackUpdateHandler.buildKeyboard()
            );
        } catch (UserNutritionProfileValidationException e) {
            calorieTelegramBot.sendReturnedMessage(
                    update.getCallbackQuery().getMessage().getChatId(),
                    e.getMessage()
            );
        } catch (InsufficientProfileDataException e) {
            calorieTelegramBot.sendReturnedMessage(
                    update.getCallbackQuery().getMessage().getChatId(),
                    e.getMessage()
            );
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_PROFILE_RECALCULATE.getCommandText();
    }
}
