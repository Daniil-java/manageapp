package com.kuklin.manageapp.bots.aiassistantcalendar.controllers;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/account/google")
@RequiredArgsConstructor
public class AccountController {
    private final TokenService tokenService;

    @GetMapping("/status/{telegramId}")
    public AssistantGoogleOAuth status(@PathVariable Long telegramId) {
        return tokenService.findByTelegramIdOrNull(telegramId);
    }

    @PostMapping("/calendar/{telegramId}")
    public void setDefaultCalendar(@PathVariable Long telegramId, @RequestParam String calendarId) {
        tokenService.setDefaultCalendarOrNull(telegramId, calendarId);
    }

    @DeleteMapping("/disconnect/{telegramId}")
    public void disconnect(@PathVariable Long telegramId) {
        tokenService.revokeAndDelete(telegramId);
    }
}
