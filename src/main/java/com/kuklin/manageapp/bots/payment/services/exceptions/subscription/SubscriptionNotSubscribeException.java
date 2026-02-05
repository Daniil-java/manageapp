package com.kuklin.manageapp.bots.payment.services.exceptions.subscription;

public class SubscriptionNotSubscribeException extends SubscriptionException{
    public static String DEF_MSG = "Attempt to create subscription from non-subscription plan";
    public SubscriptionNotSubscribeException() {
        super(DEF_MSG);
    }
    public SubscriptionNotSubscribeException(String message) {
        super(message);
    }

    public SubscriptionNotSubscribeException(String message, Throwable cause) {
        super(message, cause);
    }
}
