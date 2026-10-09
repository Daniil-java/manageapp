package com.kuklin.manageapp.payment.models;

public enum PlanPaymentResultType {
    REDIRECT_URL,              // ЮКасса / внешний провайдер
    TELEGRAM_SUBSCRIPTION_URL, // линк на подписку в TG — ⚠️ с автосписанием, сейчас не используется (только разовые оплаты)
    TELEGRAM_INVOICE           // обычный инвойс
}
