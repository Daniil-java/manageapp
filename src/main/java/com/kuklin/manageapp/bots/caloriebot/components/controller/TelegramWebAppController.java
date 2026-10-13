package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.CalorieLandingService;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.texts.LandingTexts;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Controller
@RequestMapping("/calorie")
@Hidden
@RequiredArgsConstructor
public class TelegramWebAppController {
    private static final String LANDING_ACCESS_COOKIE = "zefir_landing";
    private static final Duration LANDING_ACCESS_TTL = Duration.ofDays(30);

    private final CalorieLandingService calorieLandingService;
    private final LandingTexts landingTexts;

    /**
     * Лендинг. На zefir.fit он же открывается по корню — см. WebController.
     * Если задан calorie.landing.access-key — только по ссылке ?key=..., дальше по cookie; без ключа — 404.
     * Язык — как у privacy и terms: ?lang=ru|en, иначе по Accept-Language (getPageLanguage).
     */
    @GetMapping
    public String showLanding(@RequestParam(name = "key", required = false) String key,
                              @RequestParam(name = "lang", required = false) String lang,
                              @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
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

        AppLanguage language = getPageLanguage(lang, acceptLanguage);
        // Без ?lang= язык зависит от заголовка — кэши не должны отдавать одну версию всем
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE);
        model.addAttribute("page", calorieLandingService.getLandingPage(language));
        model.addAttribute("lang", language.getCode());
        model.addAttribute("languages", AppLanguage.values());
        model.addAttribute("t", landingTexts.getPageTexts(language));
        model.addAttribute("js", landingTexts.getScriptTexts(language));
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

    /**
     * Политика конфиденциальности. Ссылку на неё ставим в BotFather (кнопка в профиле бота) — открывается всем.
     * Язык: ?lang=ru|en (так ссылаются бот и страницы друг на друга), иначе по Accept-Language.
     */
    @GetMapping("/privacy")
    public String showPrivacyPolicy(@RequestParam(name = "lang", required = false) String lang,
                                    @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return "calorieprivacy_" + getPageLanguage(lang, acceptLanguage).getCode();
    }

    @GetMapping("/terms")
    public String showTerms(@RequestParam(name = "lang", required = false) String lang,
                            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return "calorieterms_" + getPageLanguage(lang, acceptLanguage).getCode();
    }

    /**
     * ?lang= → первый язык из Accept-Language, который у нас есть → русский, если заголовка нет.
     * Заголовок есть, но ни одного нашего языка в нём — английский.
     */
    static AppLanguage getPageLanguage(String lang, String acceptLanguage) {
        Optional<AppLanguage> fromParam = AppLanguage.fromCode(lang);
        if (fromParam.isPresent()) return fromParam.get();
        if (acceptLanguage == null || acceptLanguage.isBlank()) return AppLanguage.RU;
        try {
            return Locale.LanguageRange.parse(acceptLanguage).stream()
                    .map(range -> AppLanguage.fromCode(range.getRange()))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElse(AppLanguage.EN);
        } catch (IllegalArgumentException e) {
            return AppLanguage.RU; // кривой заголовок
        }
    }
}