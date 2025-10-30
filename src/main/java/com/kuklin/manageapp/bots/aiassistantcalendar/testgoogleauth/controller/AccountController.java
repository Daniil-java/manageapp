package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.controller;

import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/account/google")
@RequiredArgsConstructor
public class AccountController {
    private final TokenService tokenService;

    @GetMapping("/status/{telegram_id}")
    public AssistantGoogleOAuth status(@PathVariable Long telegram_id) {
        return tokenService.get(telegram_id);
    }

    @PostMapping("/calendar/{telegram_id}")
    public void setDefaultCalendar(@PathVariable Long telegram_id, @RequestParam String calendarId) {
        tokenService.setDefaultCalendar(telegram_id, calendarId);
    }

    @DeleteMapping("/disconnect/{telegram_id}")
    public void disconnect(@PathVariable Long telegram_id) {
        tokenService.revokeAndDelete(telegram_id);
    }
}
