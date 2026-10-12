package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.startflow;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UtmService;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.models.landing.CalculatorStartParam;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.KeyboardCalorieUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalculatorProfileUpdateHandler;
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
    private final CalculatorProfileUpdateHandler calculatorProfileUpdateHandler;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        keyboardCalorieUpdateHandler.updateKeyboard(update.getMessage().getChatId(), telegramUser.getAppUserId());
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                START_MESSAGE,
                getStartFlowKeyboard(),
                null
        );
        processStartParam(update, telegramUser);

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

    /**
     * Параметр /start: UTM-код или данные калькулятора лендинга с UTM-кодом в хвосте
     * (см. CalculatorStartParam).
     */
    private void processStartParam(Update update, TelegramUser telegramUser) {
        String[] parts = update.getMessage().getText().split(TelegramBot.DEFAULT_DELIMETER);
        if (parts.length < 2) return;

        CalculatorStartParam param = CalculatorStartParam.parse(parts[1]);
        if (param.utmCode() != null) {
            utmService.processClick(param.utmCode(), telegramUser.getAppUserId());
        }
        if (param.calculator() != null) {
            calculatorProfileUpdateHandler.offer(
                    update.getMessage().getChatId(), telegramUser.getAppUserId(), param.calculator());
        }
    }

    //Сообщение с просьбой заполнить профиль
    private void sendUserNutritionFillRequest(Update update, TelegramUser telegramUser) {
        UserNutritionProfile profile = userNutritionProfileService
                .getOrCreateProfile(telegramUser.getAppUserId());
        if (!profile.checkTargetCalculateParams()) {
            calorieNutritionProfileUpdateHandler.handle(update, telegramUser);
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_START.getCommandText();
    }
}
