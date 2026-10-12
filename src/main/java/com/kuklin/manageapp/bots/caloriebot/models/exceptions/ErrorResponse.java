package com.kuklin.manageapp.bots.caloriebot.models.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.Date;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private Date timestamp;
    private int status;
    private ErrorStatus errorCode;
    private String reasonPhrase;
    private String message;
    private String error;
    private String path;
    // Значения для текста ошибки на языке клиента ({"max": 4096}); message — тот же текст по-английски
    private Map<String, Object> params;

    public ErrorResponse(String message, HttpStatusCode status, WebRequest request) {
        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        this.timestamp = new Date();
        this.error = httpStatus.getReasonPhrase();
        this.message = message;
        this.status = httpStatus.value();
        this.path = ((ServletWebRequest) request).getRequest().getRequestURL().toString();
    }

    /** @param message текст ошибки по-английски — CalorieTexts.errorForApi(e) */
    public ErrorResponse(ErrorResponseException e, String message, WebRequest request) {
        this.status = e.getErrorStatus().getHttpStatus().value();
        this.reasonPhrase = e.getErrorStatus().getHttpStatus().getReasonPhrase();
        this.message = message;
        this.errorCode = e.getErrorStatus();
        this.params = e.getParams().isEmpty() ? null : e.getParams();
        this.path = ((ServletWebRequest) request).getRequest().getRequestURL().toString();
        this.timestamp = new Date();
    }
}