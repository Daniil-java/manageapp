package com.kuklin.manageapp.bots.caloriebot.components;

import com.kuklin.manageapp.bots.caloriebot.components.services.security.RequestSizeLimitFilter;
import com.kuklin.manageapp.bots.caloriebot.configurations.AiInputLimitsProperties;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponse;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;

@ControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final AiInputLimitsProperties aiInputLimits;

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e, WebRequest request) {
        return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.UNAUTHORIZED, request), HttpStatus.UNAUTHORIZED);
    }

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

    // Тело без Content-Length оборвал RequestSizeLimitFilter — это не «кривой JSON», а 413
    @Nullable
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof RequestSizeLimitFilter.RequestTooLargeException) {
                ErrorResponseException e = new ErrorResponseException(
                        ErrorStatus.REQUEST_TOO_LARGE, RequestSizeLimitFilter.tooLargeMessage(aiInputLimits));
                return new ResponseEntity<>(new ErrorResponse(e, request), HttpStatus.PAYLOAD_TOO_LARGE);
            }
        }
        return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }
}
