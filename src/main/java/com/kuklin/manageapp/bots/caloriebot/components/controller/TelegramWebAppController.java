package com.kuklin.manageapp.bots.caloriebot.components.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/calorie")
public class TelegramWebAppController {

    @GetMapping("/instruction")
    public String showInstruction() {
        return "calorieinstruction1";
    }

    @GetMapping("/utm-form")
    public String showUtmCreatingForm() {
        return "calorieutmcreate";
    }
}