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

    @GetMapping("/status/{chatId}")
    public AssistantGoogleOAuth status(@PathVariable long chatId) {
        return tokenService.get(chatId);
    }

    @PostMapping("/calendar/{chatId}")
    public void setDefaultCalendar(@PathVariable long chatId, @RequestParam String calendarId) {
        tokenService.setDefaultCalendar(chatId, calendarId);
    }

    @DeleteMapping("/disconnect/{chatId}")
    public void disconnect(@PathVariable long chatId) {
        tokenService.revokeAndDelete(chatId);
    }
}
