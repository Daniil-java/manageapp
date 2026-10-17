package com.kuklin.manageapp.bots.caloriebot.models.exceptions;

import lombok.Data;

@Data
public class ErrorResponseException extends RuntimeException{
    private final ErrorStatus errorStatus;
    // Сообщение для клиента вместо стандартного из ErrorStatus (например, с конкретным лимитом); может быть null
    private final String clientMessage;

    public ErrorResponseException(ErrorStatus errorStatus, Throwable ex) {
        super(ex);
        this.errorStatus = errorStatus;
        this.clientMessage = null;
    }

    public ErrorResponseException(ErrorStatus errorStatus) {
        super(errorStatus.getMessage());
        this.errorStatus = errorStatus;
        this.clientMessage = null;
    }

    public ErrorResponseException(ErrorStatus errorStatus, String clientMessage) {
        super(clientMessage);
        this.errorStatus = errorStatus;
        this.clientMessage = clientMessage;
    }
}
