package com.kuklin.manageapp.common.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/resume")
    public String getResumePage() {
        return "resume";
    }

    @GetMapping("/freelance")
    public String getFreelancePage() {
        return "freelance";
    }
}
