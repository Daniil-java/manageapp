package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalErrorHandler {
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> badState(IllegalStateException e) {
        log.warn("400 {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(e.getMessage() + ". Набери /auth в боте ещё раз.");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> any(Exception e) {
        log.error("500", e);
        return ResponseEntity.status(500).body("Internal error. См. логи.");
    }
}
