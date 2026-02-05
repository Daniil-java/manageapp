package com.kuklin.manageapp.bots.payment.services.exceptions.payment;

public class PaymentUnknownStatus extends PaymentException{
    public static String DEF_MSG = "Payment unknown status!";

    public PaymentUnknownStatus() {
        super(DEF_MSG);
    }
    public PaymentUnknownStatus(String message) {
        super(message);
    }

    public PaymentUnknownStatus(String message, Throwable cause) {
        super(message, cause);
    }
}
