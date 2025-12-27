package com.kuklin.manageapp.bots.payment.services.exceptions.subscription;

public class SubscriptionInvalidDataException extends SubscriptionException{
    public static String DEF_MSG = "Subscription plan has invalid data";
    public SubscriptionInvalidDataException() {
        super(DEF_MSG);
    }
    public SubscriptionInvalidDataException(String message) {
        super(message);
    }

    public SubscriptionInvalidDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
