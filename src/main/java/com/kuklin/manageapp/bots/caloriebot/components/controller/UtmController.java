package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.UtmService;
import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmLink;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/calorie/utm")
@RequiredArgsConstructor
public class UtmController {
    private final UtmService utmService;

    @GetMapping("/ownertypes")
    public List<UtmLink.OwnerType> getOwnerTypesList() {
        return Arrays.stream(UtmLink.OwnerType.values()).toList();
    }
}
