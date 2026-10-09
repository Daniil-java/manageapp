package com.kuklin.manageapp.bots.hhparserbot.controllers;

import com.kuklin.manageapp.bots.hhparserbot.services.HhVacancyService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hhbot/vacancy")
@RequiredArgsConstructor
@Slf4j
@Hidden
public class VacancyController {
    private final HhVacancyService vacancyService;

    @GetMapping("/count")
    public Long getCount() {
        return vacancyService.getCount();
    }
}
