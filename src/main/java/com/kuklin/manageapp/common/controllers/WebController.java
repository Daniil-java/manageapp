package com.kuklin.manageapp.common.controllers;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Hidden
public class WebController {
    private static final String CALORIE_DOMAIN = "zefir.fit";

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

    // zefir.fit — домен калорийного бота: в корне лендинг (/calorie), на kuklin.dev — личная страница
    @GetMapping
    public String getRootPage(HttpServletRequest request) {
        String host = request.getServerName().toLowerCase();
        boolean calorieDomain = host.equals(CALORIE_DOMAIN) || host.endsWith("." + CALORIE_DOMAIN);
        return calorieDomain ? "forward:/calorie" : "personal";
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
