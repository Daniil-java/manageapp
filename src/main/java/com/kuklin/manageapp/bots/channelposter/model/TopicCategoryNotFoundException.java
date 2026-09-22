package com.kuklin.manageapp.bots.channelposter.model;

import lombok.Getter;

@Getter
public class TopicCategoryNotFoundException extends Exception {
    public static final String DEF_MSG = "TopicCategory not found! channel_topic_category";

    public TopicCategoryNotFoundException() {
        super(DEF_MSG);
    }

    protected TopicCategoryNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
