package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.startflow;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UtmService;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.KeyboardCalorieUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.paymentpart.SubscriptionStatusCalorieUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StartUpdateHandler implements CalorieBotUpdateHandler {

    private static final String START_MESSAGE =
            """
                    Добро пожаловать! 👋 Я превращаю фото еды в точные данные о калориях и БЖУ. Забудь о ручном вводе и весах: просто сфотографируй тарелку — и ты сразу увидишь свой прогресс на удобных графиках и шкалах. Это самый быстрый способ держать форму под контролем!
                                        
                    Кстати, я запустил канал ЗЕФИР, где рассказываю о питании и ИИ. Там мы разбираем мифы о диетах, делимся научными фактами и играем в интерактивы «Угадай КБЖУ по фото». Подписывайся, там много пользы и немного юмора: @zephyr_ai (или ссылка на канал).
                                        
                    Начиная работу с ботом, вы принимаете условия <a href="https://kuklin.dev/calorie/privacy">Политики конфиденциальности</a> и <a href="https://kuklin.dev/calorie/terms">Пользовательского соглашения</a>.
                    """;
    private final UserSubscriptionService userSubscriptionService;
    private final CalorieTelegramBot calorieTelegramBot;
    private final SubscriptionStatusCalorieUpdateHandler subscriptionStatusCalorieUpdateHandler;
    private final UserNutritionProfileService userNutritionProfileService;
    private final CalorieNutritionProfileUpdateHandler calorieNutritionProfileUpdateHandler;
    private final UtmService utmService;
    private final KeyboardCalorieUpdateHandler keyboardCalorieUpdateHandler;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        keyboardCalorieUpdateHandler.updateKeyboard(update.getMessage().getChatId());
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                START_MESSAGE,
                getStartFlowKeyboard(),
                null
        );
        if (update.hasMessage() &&
                update.getMessage().getText().split(TelegramBot.DEFAULT_DELIMETER).length > 1) {
            processUtm(update, telegramUser);
        }

        //Сообщение с просьбой заполнить профиль
//        sendUserNutritionFillRequest(update, telegramUser);

        //Пробный период
        UserSubscription userSubscription = userSubscriptionService
                .createSubscriptionByFreePlanOrNull(
                        telegramUser.getAppUserId(), BotIdentifier.CALORIE_BOT);
        //Если null - значит пробный период уже был у пользователя
        if (userSubscription != null) {
            String text = subscriptionStatusCalorieUpdateHandler.getSubscriptionStatusMessage(
                    telegramUser.getAppUserId(),
                    userSubscription
            );
            calorieTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    text
            );
        }
    }

    private InlineKeyboardMarkup getStartFlowKeyboard() {
        return new TelegramKeyboard.TelegramKeyboardBuilder()
                .row(TelegramKeyboard.button(
                        "Далее",
                        Command.CALORIE_START_FLOW_1.getCommandText())
                ).row(TelegramKeyboard.button(
                        "Закрыть",
                        Command.CALORIE_CLOSE.getCommandText()
                        )
                ).build();
    }

    private void processUtm(Update update, TelegramUser telegramUser) {
        String code = update.getMessage().getText().split(TelegramBot.DEFAULT_DELIMETER)[1];
        utmService.processClick(code, telegramUser.getAppUserId());
    }

    //Сообщение с просьбой заполнить профиль
    private void sendUserNutritionFillRequest(Update update, TelegramUser telegramUser) {
        UserNutritionProfile profile = userNutritionProfileService
                .getOrCreateProfile(telegramUser.getAppUserId());
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
        subRow.add(Command.CALORIE_UTM.getCommandText());

        // Собираем в список в том порядке, в котором они должны идти в интерфейсе
        markup.setKeyboard(List.of(
                statisticsRow,
                profileRow,
                settingsRow,
                reportRow,
                subRow
        ));

        return markup;
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_START.getCommandText();
    }
}
