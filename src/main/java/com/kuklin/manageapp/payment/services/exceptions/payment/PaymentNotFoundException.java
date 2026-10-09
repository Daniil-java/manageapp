package com.kuklin.manageapp.payment.services.exceptions.payment;

//Запись платежа не найдена
public class PaymentNotFoundException extends PaymentException{
    public static final String DEF_MSG = "Payment not found!";

    public PaymentNotFoundException() {
        super(DEF_MSG);
    }
    public PaymentNotFoundException(String message) {
        super(message);
    }

    public PaymentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

