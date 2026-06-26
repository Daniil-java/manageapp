package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.User;

@RestController
@RequestMapping("/calorie/users")
@RequiredArgsConstructor
public class CalorieTelegramUsersController {

    private final TelegramUserService telegramUserService;

    @GetMapping
    public TelegramUser getTelegramUserByTelegramId(@RequestParam Long appUserId) {
        return telegramUserService.getTelegramUserByTelegramIdAndBotIdentifierOrNull(appUserId, BotIdentifier.CALORIE_BOT);
    }

//    @PostMapping
//    public TelegramUser createTelegramUserByTelegramId(@AuthenticationPrincipal Long tgUserId) {
//        return telegramUserService.createOrGetUserByTelegram(BotIdentifier.CALORIE_BOT, user);
//    }
}
