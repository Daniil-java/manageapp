package com.kuklin.manageapp.bots.aiassistantcalendar.controllers;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;


@Controller
@RequestMapping("/privacy_policy")
@Hidden
public class PageController {

    @GetMapping
    public String privacy() {
        return "forward:/privacy_policy.html";
    }
}
