package com.kuklin.manageapp.bots.caloriebot.components.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/calorie")
@Hidden
public class TelegramWebAppController {

    @GetMapping("/test1")
    public String getTest1() {
        return "controllertest1";
    }

    @GetMapping("/v2/test2")
    public String getTest2() {
        return "controllertest2";
    }

    @GetMapping("/instruction")
    public String showInstruction() {
        return "calorieinstruction1";
    }

    @GetMapping("/utm-form")
    public String showUtmCreatingForm() {
        return "calorieutmcreate";
    }

    @GetMapping("/privacy")
    public String showPrivacyPolicy() {
        return "calorieprivacy";
    }

    @GetMapping("/terms")
    public String showTerms() {
        return  "calorieterms";
    }
}