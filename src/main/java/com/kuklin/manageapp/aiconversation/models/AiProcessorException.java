package com.kuklin.manageapp.aiconversation.models;

import lombok.Getter;

@Getter
public class AiProcessorException extends Exception {
    public static final String DEF_MSG = "AI processor error!";

    public AiProcessorException() {
        super(DEF_MSG);
    }

    public AiProcessorException (String message) {
        super(message);
    }

    public AiProcessorException(String message, Throwable cause) {
        super(message, cause);
    }
}
