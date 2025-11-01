package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.models;

public class TokenRefreshException extends Exception {
    public enum Reason { INVALID_GRANT, OTHER }
    private final Reason reason;
    private final int status;
    private final String responseBody;

    public TokenRefreshException(Reason reason, String message, int status, String responseBody, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.status = status;
        this.responseBody = responseBody;
    }

    public Reason getReason() { return reason; }
    public int getStatus() { return status; }
    public String getResponseBody() { return responseBody; }
}
