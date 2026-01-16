package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditFieldHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.KeyboardTemplates.SET_CMD;
import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction.*;

/*
* Обработчик команды редактирования полей профиля
* Отвечает только на колбэки
*
* Отвечает за:
* - Выбор пользователем изменяемого поля
* - Возврат соответствующего сообщения с клавиатурой
*
 */
@Component
public class NutritionProfileDialogueUpdateHandler implements CalorieBotUpdateHandler {

    private final CalorieTelegramBot calorieTelegramBot;
    private final UserNutritionProfileService userNutritionProfileService;
    private final CalorieNutritionProfileUpdateHandler calorieNutritionProfileUpdateHandler;
    //Мапа для выдачи нужного обработчика изменяемых полей
    private final Map<ProfileEditAction, ProfileEditFieldHandler> actionHandlers;

    //Мапа для определения последовательности диалога
    private static final Map<ProfileEditAction, ProfileEditAction> NEXT_ACTION =
            new EnumMap<>(ProfileEditAction.class);

    static {
        NEXT_ACTION.put(SEX, AGE);
        NEXT_ACTION.put(AGE, HEIGHT);
        NEXT_ACTION.put(HEIGHT, CURRENT_WEIGHT);
        NEXT_ACTION.put(CURRENT_WEIGHT, ACTIVITY);
        NEXT_ACTION.put(ACTIVITY, GOAL);
        NEXT_ACTION.put(GOAL, DIET_TYPE);
    }

    public NutritionProfileDialogueUpdateHandler(
            List<ProfileEditFieldHandler> handlerList,
            CalorieTelegramBot calorieTelegramBot,
            UserNutritionProfileService userNutritionProfileService,
            CalorieNutritionProfileUpdateHandler calorieNutritionProfileUpdateHandler
    ) {
        this.calorieTelegramBot = calorieTelegramBot;
        this.userNutritionProfileService = userNutritionProfileService;
        this.calorieNutritionProfileUpdateHandler = calorieNutritionProfileUpdateHandler;

        this.actionHandlers = new EnumMap<>(ProfileEditAction.class);
        for (ProfileEditFieldHandler handler : handlerList) {
            this.actionHandlers.put(handler.getProfileAction(), handler);
        }
    }

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        CallbackQuery query = update.getCallbackQuery();
        String data = query.getData();

        //Получение профиля пользователя
        UserNutritionProfile profile = userNutritionProfileService
                .getOrCreateProfile(telegramUser.getTelegramId());

        // старт диалога
        if (data.equals(getHandlerListName())) {
            //Вызвыть первый вопрос
            query.setData(
                    data + TelegramBot.DEFAULT_DELIMETER + SEX
            );
            handleAction(SEX, profile, update, telegramUser);
            return;
        }

        //Извлекаем текущее действие из возвращаемого колбэка
        ProfileEditAction action = findActionOrNull(data);

        //Команда SET присутствует тогда, когда нужно переключаться на следующий хендер
        //Если этой команды нет, значит обработчик еще не закончил свои операции
        if (!data.contains(SET_CMD)) {
            handleAction(action, profile, update, telegramUser);
            return;
        }

        //Определяем следующий обработчик, после текущего
        ProfileEditAction nextAction = NEXT_ACTION.get(action);

        //Если мы переходим к следующему обработчику
        if (nextAction != null) {
            //Завершающая операция для обработчика
            handleAction(action, profile, update, telegramUser);
            //Изменяем данные в колбэке таким образом, чтобы мапа выдала нужный обработчик
            update.getCallbackQuery().setData(getNewAction(data, nextAction));
            //Начало операций нового разработчика
            handleAction(nextAction, profile, update, telegramUser);
        } else {
            //Остаемся в текущем обработчике
            calorieNutritionProfileUpdateHandler.handle(update, telegramUser);
        }
    }

    //Новые данные в колбэк
    private String getNewAction(String data, ProfileEditAction action) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            parts[1] = action.getCode();

            return parts[0] + TelegramBot.DEFAULT_DELIMETER + parts[1];
        } catch (Exception e) {
            return null;
        }
    }

    //Получение обработчика из мапы и обработка
    private void handleAction(
            ProfileEditAction action,
            UserNutritionProfile profile,
            Update update,
            TelegramUser telegramUser
    ) {
        actionHandlers.get(action).handle(
                Command.CALORIE_PROFILE_DIALOGUE,
                calorieTelegramBot,
                profile,
                update,
                telegramUser
        );
    }

    //Извлечение данных из колбэка
    private ProfileEditAction findActionOrNull(String data) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return ProfileEditAction.fromCode(parts[1]);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_PROFILE_DIALOGUE.getCommandText();
    }
}
