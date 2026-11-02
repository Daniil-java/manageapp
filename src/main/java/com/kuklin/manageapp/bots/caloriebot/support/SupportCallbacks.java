package com.kuklin.manageapp.bots.caloriebot.support;

/**
 * Кнопки поддержки у пользователя: callback_data «/support &lt;действие&gt; …» — обрабатывает
 * SupportCalorieUpdateHandler (та же команда, что /support).
 */
public final class SupportCallbacks {
    private SupportCallbacks() {
    }

    /** «/support cat PAYMENT» — тема выбрана, ждём описание. */
    public static final String CATEGORY = "cat";
    /** «/support add 12» — «✍️ Ответить» / «Дописать в заявку»: следующее сообщение — в заявку 12. */
    public static final String APPEND = "add";
    /** «/support cancel» — не ждём сообщение. */
    public static final String CANCEL = "cancel";
    /** «/support rate 12 up» — оценка закрытой заявки. */
    public static final String RATE = "rate";
    public static final String RATE_UP = "up";
    public static final String RATE_DOWN = "down";
}
