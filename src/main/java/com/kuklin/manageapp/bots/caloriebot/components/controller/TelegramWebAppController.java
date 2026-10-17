package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.CalorieLandingService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Controller
@RequestMapping("/calorie")
@Hidden
@RequiredArgsConstructor
public class TelegramWebAppController {
    private static final String LANDING_ACCESS_COOKIE = "zefir_landing";
    private static final Duration LANDING_ACCESS_TTL = Duration.ofDays(30);

    private final CalorieLandingService calorieLandingService;

    /**
     * Лендинг. На zefir.fit он же открывается по корню — см. WebController.
     * Если задан calorie.landing.access-key — только по ссылке ?key=..., дальше по cookie; без ключа — 404.
     */
    @GetMapping
    public String showLanding(@RequestParam(name = "key", required = false) String key,
                              @CookieValue(name = LANDING_ACCESS_COOKIE, required = false) String cookieKey,
                              HttpServletResponse response,
                              Model model) {
        if (calorieLandingService.isClosed()) {
            if (calorieLandingService.isAccessKeyValid(key)) {
                ResponseCookie cookie = ResponseCookie.from(LANDING_ACCESS_COOKIE, key)
                        .path("/")
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Lax")
                        .maxAge(LANDING_ACCESS_TTL)
                        .build();
                response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            } else if (!calorieLandingService.isAccessKeyValid(cookieKey)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            // Пока лендинг закрыт — не индексировать (ссылка с ключом не должна попасть в поиск)
            response.setHeader("X-Robots-Tag", "noindex, nofollow");
        }

        model.addAttribute("page", calorieLandingService.getLandingPage());
        return "calorielanding";
    }

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