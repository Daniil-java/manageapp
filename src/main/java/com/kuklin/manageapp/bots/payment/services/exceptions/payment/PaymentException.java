package com.kuklin.manageapp.bots.payment.services.exceptions.payment;

import lombok.Getter;

@Getter
public class PaymentException extends Exception{
    public PaymentException(String message) {
        super(message);
    }

    protected PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
