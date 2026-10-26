package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.StreakService;
import com.kuklin.manageapp.bots.caloriebot.models.report.StreakDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/calorie/streak")
@RequiredArgsConstructor
@Tag(name = "Серия дней", description = "Дни подряд с записями еды или воды")
public class StreakController {
    private final StreakService streakService;

    @GetMapping
    @Operation(summary = "Серия дней",
            description = "Текущая и лучшая серия, есть ли записи сегодня и последние 7 дней (последний — сегодня). "
                    + "День засчитывается, если записана еда или вода")
    public StreakDto getStreak(@Parameter(hidden = true) @AuthenticationPrincipal Long appUserId) {
        return streakService.getStreak(appUserId);
    }
}
