package com.kuklin.manageapp.bots.aiassistantcalendar.controllers;

import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth/google")
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthController {
    private final GoogleOAuthService googleOAuthService;

    /**
     * Бот шлёт ссылку вида:
     *   https://<host>/auth/google/start?linkId=<UUID>
     */
    @GetMapping("/start")
    public ResponseEntity<?> start(@RequestParam(value = "linkId", required = false) String linkId) {
        return googleOAuthService.start(linkId);
    }

    /**
     * Колбэк Google: может прийти как (code,state), так и (error,error_description,state).
     */
    @GetMapping("/callback")
    public ResponseEntity<?> callback(@RequestParam(value = "code", required = false) String code,
                                      @RequestParam("state") UUID state,
                                      @RequestParam(value = "error", required = false) String error,
                                      @RequestParam(value = "error_description", required = false) String errorDescription) {

        return googleOAuthService.callback(code, state, error, errorDescription);
    }
}
