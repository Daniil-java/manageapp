package com.kuklin.manageapp.common.library.tgmodels;

import lombok.Data;
import lombok.experimental.Accessors;
import org.telegram.telegrambots.meta.api.methods.invoices.CreateInvoiceLink;

/**
 * ⚠️ Подписка Telegram Stars с АВТОСПИСАНИЕМ — сейчас не используется, оплаты только разовые (et-81).
 * Осторожно, если включать:
 * - Telegram сам списывает звёзды каждые subscriptionPeriod секунд, пока пользователь не отменит;
 *   период — только 2592000 (30 дней). Годится лишь для 30-дневного тарифа — тариф на 90 дней
 *   превратится в списание полной цены каждые 30 дней.
 * - telegrambots 6.9 не знает subscription_period: без @JsonProperty("subscription_period") поле
 *   уходит как "subscriptionPeriod", Telegram его молча игнорирует, и счёт становится разовым.
 * - Продления приходят боту как successful_payment с тем же payload — см. PaymentService.processTelegramSubs
 *   (вживую не проверено).
 */
@Data
@Accessors(chain = true)
public class CreateInvoiceLinkWithTelegramSubscription extends CreateInvoiceLink {

    private final Integer subscriptionPeriod;
}
