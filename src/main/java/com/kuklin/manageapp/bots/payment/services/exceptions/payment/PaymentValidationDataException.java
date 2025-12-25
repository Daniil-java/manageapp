package com.kuklin.manageapp.bots.payment.services.exceptions.payment;

public class PaymentValidationDataException extends PaymentException {
    public static String DEF_MSG = "Payment validation error!";
    public PaymentValidationDataException() {
        super(DEF_MSG);
    }
    public PaymentValidationDataException(String message) {
        super(message);
    }

    public PaymentValidationDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
