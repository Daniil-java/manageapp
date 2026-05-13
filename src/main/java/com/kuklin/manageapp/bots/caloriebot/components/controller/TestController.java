package com.kuklin.manageapp.bots.caloriebot.components.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
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
