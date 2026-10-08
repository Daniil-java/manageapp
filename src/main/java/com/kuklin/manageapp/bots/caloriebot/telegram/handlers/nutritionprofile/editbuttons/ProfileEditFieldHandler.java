package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface ProfileEditFieldHandler {
    void handle(Command command, TelegramBot telegramBot, UserNutritionProfile profile, Update update, TelegramUser telegramUser);
    ProfileEditAction getProfileAction();

    @Autowired
    default void registerMyself(CalorieNutritionProfileEditCallbackUpdateHandler calorieNutritionProfileEditCallbackUpdateHandler) {
        calorieNutritionProfileEditCallbackUpdateHandler
                .register(getProfileAction(), this);
    }
}
