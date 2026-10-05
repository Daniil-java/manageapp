package com.kuklin.manageapp.common.controllers;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Hidden
public class WebController {

    // /auth, /login, /register, /dashboard ниже отдают тестовые Thymeleaf-шаблоны, а "/" редиректит на
    // /dashboard. Решено оставить их в коде и защитить в SecurityConfig (hasRole("ADMIN")), а не удалять.

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

    @GetMapping("/auth")
    public String getAuthPage() {
        return "authtest";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @GetMapping("/dashboard")
    public String dashboardPage() {
        return "dashboard";
    }
}
