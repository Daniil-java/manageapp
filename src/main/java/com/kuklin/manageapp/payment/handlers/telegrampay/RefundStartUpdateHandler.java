package com.kuklin.manageapp.payment.handlers.telegrampay;

import com.kuklin.manageapp.common.components.TelegramBotRegistry;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.handlers.PaymentUpdateHandler;
import com.kuklin.manageapp.payment.models.StarsRefundClient;
import com.kuklin.manageapp.payment.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Set;

/**
 * /refund &lt;charge_id&gt; в платёжном боте — вернуть звёзды за платёж. Только админы (payment.admin-telegram-ids),
 * остальным бот не отвечает.
 * <p>
 * Звёзды возвращает бот, который принял оплату (payment.botIdentifier), — его токеном и на Telegram id плательщика.
 * Сначала refundStarPayment в Telegram, и только если он прошёл — отмена в БД (подписка, генерации). Раньше было
 * наоборот: отмена в БД шла первой, и неудачный возврат оставлял пользователя без подписки и без звёзд.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundStartUpdateHandler implements PaymentUpdateHandler {
    private final PaymentService paymentService;
    private final TelegramBotRegistry telegramBotRegistry;
    private final TelegramUserService telegramUserService;

    @Value("${payment.admin-telegram-ids}")
    private Set<Long> adminTelegramIds;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!adminTelegramIds.contains(telegramUser.getTelegramId())) {
            log.warn("Refund attempt by non-admin {}", telegramUser.getTelegramId());
            return;
        }
        Long chatId = update.getMessage().getChatId();
        TelegramBot adminBot = telegramBotRegistry.get(telegramUser.getBotIdentifier());

        String[] parts = update.getMessage().getText().split(TelegramBot.DEFAULT_DELIMETER);
        if (parts.length < 2 || parts[1].isBlank()) {
            adminBot.sendReturnedMessage(chatId, "Формат: /refund <telegram_payment_charge_id>");
            return;
        }
        String chargeId = parts[1].trim();
        adminBot.sendReturnedMessage(chatId, refundAndDescribe(chargeId));
    }

    private String refundAndDescribe(String chargeId) {
        Payment payment = paymentService.getRefundableTelegramPaymentOrNull(chargeId);
        if (payment == null) {
            return "❌ Платёж не найден, не в звёздах или уже не в статусе SUCCESS: " + chargeId;
        }
        TelegramUser payer = telegramUserService
                .findByAppUserIdAndBotIdentifier(payment.getAppUserId(), payment.getBotIdentifier())
                .orElse(null);
        if (payer == null) {
            return "❌ Не найден Telegram-пользователь плательщика: appUserId " + payment.getAppUserId();
        }

        try {
            TelegramBot paymentBot = telegramBotRegistry.get(payment.getBotIdentifier());
            if (!buildStarsRefundClient(paymentBot.getToken()).refund(payer.getTelegramId(), chargeId)) {
                return "❌ Telegram не вернул звёзды (refundStarPayment), в БД ничего не менял: " + chargeId;
            }
        } catch (Exception e) {
            log.error("Stars refund failed: {}", chargeId, e);
            return "❌ Ошибка запроса к Telegram, в БД ничего не менял: " + chargeId;
        }

        // Звёзды уже у пользователя — дальше только учёт: ошибку здесь админ правит руками
        if (!paymentService.refundTelegramPayment(chargeId)) {
            log.error("Stars refunded but payment {} not marked REFUNDED", chargeId);
            return "⚠️ Звёзды вернули, но платёж в БД не отмечен возвратом — проверь вручную: " + chargeId;
        }
        return "✅ Возврат выполнен: платёж " + payment.getId() + ", " + payment.getStarsAmount()
                + " ⭐, бот " + payment.getBotIdentifier() + ", пользователь " + payer.getTelegramId();
    }

    StarsRefundClient buildStarsRefundClient(String botToken) {
        return new StarsRefundClient(botToken);
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_REFUND.getCommandText();
    }
}
