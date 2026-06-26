package com.kuklin.manageapp.bots.caloriebot.components.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class TestController {

    @GetMapping("/calorie/test")
    public String getTestData() {
        return "test1";
    }

    @GetMapping("test")
    public String getTestDataSec() {
        return "test2";
    }
}
