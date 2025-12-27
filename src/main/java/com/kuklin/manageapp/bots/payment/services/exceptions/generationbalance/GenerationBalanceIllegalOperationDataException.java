package com.kuklin.manageapp.bots.payment.services.exceptions.generationbalance;

public class GenerationBalanceIllegalOperationDataException extends GenerationBalanceException{
    public static String DEF_MSG = "Illegal operation for generation balance!";
    public GenerationBalanceIllegalOperationDataException() {
        super(DEF_MSG);
    }
    public GenerationBalanceIllegalOperationDataException(String message) {
        super(message);
    }

    public GenerationBalanceIllegalOperationDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
