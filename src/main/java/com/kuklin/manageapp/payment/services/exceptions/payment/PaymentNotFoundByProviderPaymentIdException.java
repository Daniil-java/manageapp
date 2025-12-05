package com.kuklin.manageapp.payment.services.exceptions.payment;

public class PaymentNotFoundByProviderPaymentIdException extends PaymentException{
    public static final String DEF_MSG = "Payment not found by provider payment id!";

    public PaymentNotFoundByProviderPaymentIdException() {
        super(DEF_MSG);
    }
    public PaymentNotFoundByProviderPaymentIdException(String message) {
        super(message);
    }

    public PaymentNotFoundByProviderPaymentIdException(String message, Throwable cause) {
        super(message, cause);
    }
}
