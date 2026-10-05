package com.kuklin.manageapp.payment.services.exceptions.generationbalance;

public class GenerationBalanceNotEnoughBalanceException extends GenerationBalanceException{
    public static final String DEF_MSG = "Not enough generation balance!";
    public GenerationBalanceNotEnoughBalanceException() {
        super(DEF_MSG);
    }
    public GenerationBalanceNotEnoughBalanceException(String message) {
        super(message);
    }

    public GenerationBalanceNotEnoughBalanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
