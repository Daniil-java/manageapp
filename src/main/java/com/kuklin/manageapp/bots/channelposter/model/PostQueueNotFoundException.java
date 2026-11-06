package com.kuklin.manageapp.bots.channelposter.model;

import lombok.Getter;

@Getter
public class PostQueueNotFoundException extends Exception {
    public static final String DEF_MSG = "PostQueue not found! channel_topic_category";

    public PostQueueNotFoundException() {
        super(DEF_MSG);
    }

    protected PostQueueNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

}
