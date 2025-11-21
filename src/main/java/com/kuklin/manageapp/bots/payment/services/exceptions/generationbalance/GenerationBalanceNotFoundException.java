package com.kuklin.manageapp.bots.payment.services.exceptions.generationbalance;

public class GenerationBalanceNotFoundException extends GenerationBalanceException{
    public static String DEF_MSG = "User's balance of generations not found!";
    public GenerationBalanceNotFoundException() {
        super(DEF_MSG);
    }
    public GenerationBalanceNotFoundException(String message) {
        super(message);
    }

    public GenerationBalanceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
