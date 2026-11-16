package com.kuklin.manageapp.payment.services.exceptions.generationbalance;

import lombok.Getter;

@Getter
public class GenerationBalanceException extends Exception{
    public GenerationBalanceException(String message) {
        super(message);
    }

    protected GenerationBalanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
