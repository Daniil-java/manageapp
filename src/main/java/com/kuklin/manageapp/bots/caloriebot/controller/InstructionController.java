package com.kuklin.manageapp.bots.caloriebot.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller("/calorie")
public class InstructionController {

    @GetMapping("instruction/")
    public String showInstruction() {
        return "calorieinstruction1";
    }
}
