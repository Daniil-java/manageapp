package com.kuklin.manageapp.bots.payment.services.exceptions.subscription;

public class SubscriptionNotFound extends SubscriptionException{
    public static String DEF_MSG = "No subscriptions found";
    public SubscriptionNotFound() {
        super(DEF_MSG);
    }
    public SubscriptionNotFound(String message) {
        super(message);
    }

    public SubscriptionNotFound(String message, Throwable cause) {
        super(message, cause);
    }
}
