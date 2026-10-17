package com.kuklin.manageapp.bots.caloriebot.models.exceptions;

import lombok.Data;

@Data
public class ErrorResponseException extends RuntimeException{
    private final ErrorStatus errorStatus;

    public ErrorResponseException(ErrorStatus errorStatus, Throwable ex) {
        super(ex);
        this.errorStatus = errorStatus;
    }

    public ErrorResponseException(ErrorStatus errorStatus) {
        super(errorStatus.getMessage());
        this.errorStatus = errorStatus;
    }
}
