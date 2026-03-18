package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.paymentpart;

import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.handlers.PaymentProviderChoiceUpdateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class PaymentPlanListCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final PaymentProviderChoiceUpdateHandler nextHandler;
    private final CommonPaymentFacade commonPaymentFacade;

    private static final String IMAGE_PATH =
            "static/images/caloriescreens/price/tarif.png";
    private static final String PLAN_TEXT = """
            🆓 Базовый режим — «держим форму»:
            • 📸 2 фото-разбора в день — чтобы не улететь в туман калорий
            • 🧠 2 AI-консультации в день — быстрый чек-ап без занудства
            • 🎙️ Текст и голос — без лимитов, говори, не стесняйся

            💎 Premium режим — «врубаем турбо»:
            • 🚀 Фото-разборы без ограничений — хоть каждый укус фиксируй
            • 🤖 AI-консультации без стопов + PDF с разбором рациона и пищевых привычек
            • 📊 Полная статистика и история — видишь не догадки, а реальную картину
            """;


    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasCallbackQuery()
                ? update.getCallbackQuery().getMessage().getChatId()
                : update.getMessage().getChatId();

        sendSubListMessage(chatId);
    }

    public void sendSubListMessage(Long chatId) {
        List<PricingPlan> planList = commonPaymentFacade
                .getPricingPlans(calorieTelegramBot.getBotIdentifier());

        byte[] photoBytes = loadImageFromResourcesOrNull();

        if (photoBytes == null) {
            log.error("Error returning message!");
            calorieTelegramBot.sendReturnedMessage(chatId, "Ошибка возврата сообщения!");
            return;
        }

        calorieTelegramBot.sendPhotoMessage(
                chatId,
                photoBytes,
                "tarif.png",
                PLAN_TEXT,
                getKeyboardPlan(planList)
        );
    }

    private byte[] loadImageFromResourcesOrNull() {
        try {
            ClassPathResource resource = new ClassPathResource(IMAGE_PATH);
            return StreamUtils.copyToByteArray(resource.getInputStream());
        } catch (IOException e) {
            log.error("Не удалось загрузить изображение тарифа: {}", IMAGE_PATH, e);
            return null;
        }
    }

    // Формирует inline-клавиатуру с тарифами:
    // каждая кнопка содержит название плана и callback с его id.
    private InlineKeyboardMarkup getKeyboardPlan(List<PricingPlan> planList) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        for (PricingPlan plan : planList) {
            builder.row(TelegramKeyboard.button(
                    plan.getTitle(),
                    nextHandler.getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + plan.getId()
            ));
        }
        builder.row(
                TelegramKeyboard.button("\uD83D\uDD0DСтатус подписки", Command.PAYMENT_BALANCE.getCommandText())
        );
        builder.row(
                TelegramKeyboard.button("❌Закрыть", Command.CALORIE_CLOSE.getCommandText())
        );
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_PAYMENT_PAYLOAD_PLAN.getCommandText();
    }
}
