package com.kuklin.manageapp.bots.caloriebot.telegram.handlers;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.paymentpart.SubscriptionStatusCalorieUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StartUpdateHandler implements CalorieBotUpdateHandler {

    private static final String START_MESSAGE =
            """
                    Отправь фото блюда, напиши его описание или отправь голосовое сообщение, чтобы получить КБЖУ блюда!
                    Для более подробных инструкций нажми на кнопку "📖FAQ"! 
                    Чтобы вызвать меню - напиши /menu
                    """;
    private final UserSubscriptionService userSubscriptionService;
    private final CalorieTelegramBot calorieTelegramBot;
    private final SubscriptionStatusCalorieUpdateHandler subscriptionStatusCalorieUpdateHandler;
    private final UserNutritionProfileService userNutritionProfileService;
    private final CalorieNutritionProfileUpdateHandler calorieNutritionProfileUpdateHandler;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                START_MESSAGE,
                getCommandKeyboard(),
                null
        );

        sendUserNutritionFillRequest(update, telegramUser);

        //Пробный период
        UserSubscription userSubscription = userSubscriptionService
                .createSubscriptionByFreePlanOrNull(
                        telegramUser.getTelegramId(), BotIdentifier.CALORIE_BOT);
        //Если null - значит пробный период уже был у пользователя
        if (userSubscription != null) {
            String text = subscriptionStatusCalorieUpdateHandler.getSubscriptionStatusMessage(
                    telegramUser.getTelegramId(),
                    userSubscription
            );
            calorieTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    text
            );
        }
    }

    //Сообщение с просьбой заполнить профиль
    private void sendUserNutritionFillRequest(Update update, TelegramUser telegramUser) {
        UserNutritionProfile profile = userNutritionProfileService
                .getOrCreateProfile(telegramUser.getTelegramId());
        if (!profile.checkTargetCalculateParams()) {
            calorieNutritionProfileUpdateHandler.handle(update, telegramUser);
        }
    }

    public static ReplyKeyboardMarkup getCommandKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        KeyboardRow settingsRow = new KeyboardRow();
        settingsRow.add(Command.CALORIE_SETTINGS.getCommandText());
        settingsRow.add(Command.CALORIE_PROFILE.getCommandText());

        KeyboardRow profileRow = new KeyboardRow();
        profileRow.add(Command.CALORIE_WATER.getCommandText());
        profileRow.add(Command.CALORIE_FAVORITE.getCommandText());

        KeyboardRow statisticsRow = new KeyboardRow();
        statisticsRow.add(Command.CALORIE_TODAY_LIST.getCommandText());

        KeyboardRow reportRow = new KeyboardRow();
        reportRow.add(Command.CALORIE_REPORT.getCommandText());
        reportRow.add(Command.CALORIE_WELCOME.getCommandText());

        KeyboardRow subRow = new KeyboardRow();
        subRow.add(Command.CALORIE_PAYMENT_PAYLOAD_PLAN.getCommandText());

        // Собираем в список в том порядке, в котором они должны идти в интерфейсе
        markup.setKeyboard(List.of(
                profileRow,
                settingsRow,
                reportRow,
                statisticsRow,
                subRow
        ));

        return markup;
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_START.getCommandText();
    }
}
