package com.kuklin.manageapp.payment.components.texts;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Сообщения модуля оплаты пользователю. Текст по умолчанию — для ботов без своих переводов (PaymentBotTexts),
 * бот с переводами берёт текст из своего словаря по имени константы.
 */
@Getter
@RequiredArgsConstructor
public enum PaymentText {
    PLAN_LIST_TITLE("Выберите один из планов:"),
    PLAN_LIST_NOT_FOUND("Ошибка! Не получилось найти список тарифов!"),
    PLAN_NOT_FOUND("Не получилось найти тарифный план!"),
    PROVIDER_CHOICE("Выберите один из способов оплаты:"),
    PAYMENT_DATA_ERROR("Бот не смог извлечь данные о платежном формате!"),
    INVOICE_SEND_ERROR("Ошибка! Не получилось отправить форму оплаты! Попробуйте заново"),
    /** Подпись цены в счёте Telegram */
    INVOICE_PRICE_LABEL("Оплата"),
    /** Подписка Telegram Stars с автосписанием (сейчас выключена, см. CommonPaymentFacade). Markdown. */
    TELEGRAM_SUBSCRIPTION_TEXT("""
            ⭐ *Подписка на Telegram Stars*

            Для активации автопродляемой подписки нажмите на кнопку ниже.
            Оплата произойдет через ваш баланс звезд."""),
    TELEGRAM_SUBSCRIPTION_BUTTON("💳 Оформить подписку"),
    /** Ответ Telegram на pre_checkout_query, если платёж не прошёл проверку — показывается в окне оплаты */
    PRE_CHECKOUT_ERROR("Ошибка данных платежа"),
    PAYMENT_SUCCESS("Успешная оплата!"),
    PAYMENT_VALIDATION_ERROR("Ошибка валидации платежных данных"),
    PAYMENT_ERROR("Ошибка платежа! Обратитесь к администратору!"),
    REFUND_NOT_ALLOWED("Платеж не соответствует условиям возврата!"),
    SUBSCRIPTION_EXPIRED("Срок действия подписки истек!");

    private final String defaultText;
}
