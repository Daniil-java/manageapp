package com.kuklin.manageapp.common.controllers;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Hidden
public class WebController {

    @GetMapping("/resume")
    public String getResumePage() {
        return "resume";
    }

    @GetMapping("/freelance")
    public String getFreelancePage() {
        return "freelance";
    }

    @GetMapping
    public String getPersonalPage() {
        return "personal";
    }

    @GetMapping("/pomidorotimer")
    public String getPomidoro() {
        return "timer";
    }

    @GetMapping("/hhbot/skills")
    public String getSkillPage() {
        return "skillsdata";
    }
}
