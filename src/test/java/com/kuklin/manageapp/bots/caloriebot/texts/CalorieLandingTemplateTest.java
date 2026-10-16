package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.components.services.CalorieLandingService;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Шаблон лендинга рендерится на каждом языке, и в английском не остаётся русского. */
class CalorieLandingTemplateTest {

    private final LandingTexts texts = new LandingTexts(new I18nProperties());

    @Test
    void englishLandingHasNoRussian() {
        String html = render(AppLanguage.EN);

        assertThat(html).contains("<html lang=\"en\"", "Count calories from a&nbsp;photo", "Save 22%", "1 month",
                "href=\"/calorie/privacy?lang=en\"", "hreflang=\"ru\"", ">Русский</a>");
        // Кириллица — только в названии языка на переключателе и в комментариях
        String withoutSwitcher = html.replace(">Русский</a>", "")
                .replaceAll("(?s)<!--.*?-->", "").replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?s)//[^\\n]*", "");
        assertThat(withoutSwitcher).doesNotContainPattern("[А-Яа-яЁё]");
    }

    @Test
    void russianLandingRenders() {
        String html = render(AppLanguage.RU);

        assertThat(html).contains("<html lang=\"ru\"", "Считай калории по&nbsp;фото", "Выгода 22%",
                "href=\"/calorie/terms?lang=ru\"", ">English</a>");
        assertThat(html).doesNotContain("th:text", "th:utext", "Save 22%");
    }

    private String render(AppLanguage language) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        var page = new CalorieLandingService.LandingPage(
                List.of(new CalorieLandingService.PlanCard("Premium", List.of(
                        new CalorieLandingService.PlanPoint("✨", "Everything")), "350 ⭐", "1 month", 22)),
                List.of(new CalorieLandingService.FeatureRow("Feature", "2", "∞")),
                "https://t.me/calorydairy_bot");

        Context context = new Context(language.getLocale(), Map.of(
                "page", page,
                "lang", language.getCode(),
                "languages", AppLanguage.values(),
                "t", texts.getPageTexts(language),
                "js", texts.getScriptTexts(language)));
        return engine.process("calorielanding", context);
    }
}
