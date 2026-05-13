package com.kuklin.manageapp.bots.caloriebot.components;

import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponse;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> catchAErrorResponseException(ErrorResponseException e, WebRequest request) {
        return new ResponseEntity<>(new ErrorResponse(e, request), e.getErrorStatus().getHttpStatus());
    }

    @Nullable
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        StringBuilder sb = new StringBuilder(String.format("Метод %s не поддерживается", ex.getMethod()));

        if (ex.getSupportedHttpMethods() != null && ex.getSupportedMethods().length > 0) {
            sb.append(". Поддерживаемые методы: ")
                    .append(Arrays.toString(ex.getSupportedMethods()))
                    .append(".");
        }

        ErrorResponse errorResponse = new ErrorResponse(sb.toString(), status, request);
        return new ResponseEntity<>(errorResponse, headers, status);
    }
}
