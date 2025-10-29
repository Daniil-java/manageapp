//package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.old;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//
//@Slf4j
//@org.springframework.web.bind.annotation.RestControllerAdvice
//public class GlobalErrorHandler {
//    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
//    public ResponseEntity<String> badArg(IllegalArgumentException e) {
//        return ResponseEntity.badRequest().body(e.getMessage());
//    }
//
//    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
//    public ResponseEntity<String> badState(IllegalStateException e) {
//        return ResponseEntity.badRequest().body(e.getMessage());
//    }
//
//    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
//    public ResponseEntity<String> any(Exception e) {
//        log.error("500", e);
//        return ResponseEntity.status(500).body("Internal error");
//    }
//}
//
