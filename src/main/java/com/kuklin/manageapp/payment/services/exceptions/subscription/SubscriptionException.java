package com.kuklin.manageapp.payment.services.exceptions.subscription;

import lombok.Getter;

@Getter
public abstract class SubscriptionException extends Exception {

    protected SubscriptionException(String message) {
        super(message);
    }

    protected SubscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
