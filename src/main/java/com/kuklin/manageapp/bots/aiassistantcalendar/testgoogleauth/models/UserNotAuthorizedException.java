package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.models;

public class UserNotAuthorizedException extends Exception {
    public UserNotAuthorizedException(Long telegramId) {
        super("User not authorized: telegramId=" + telegramId);
    }
    public UserNotAuthorizedException(String message) {
        super(message);
    }
}

