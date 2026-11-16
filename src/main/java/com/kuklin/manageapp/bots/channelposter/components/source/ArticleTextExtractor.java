package com.kuklin.manageapp.bots.channelposter.components.source;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Достаёт текст статьи со страницы: ищет основной контейнер и собирает из него абзацы.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArticleTextExtractor {

    // текст длиннее — только расход токенов, для поста на 1500 символов хватает
    public static final int MAX_CHARS = 15_000;
    static final int MIN_CHARS = 400;

    // от самых точных к общим: ScienceDaily (#story_text/#text), schema.org, WordPress, общие теги
    private static final List<String> CONTAINERS = List.of(
            "[itemprop=articleBody]", "#story_text", "#text", ".article-main", ".article__body", ".article-body",
            ".entry-content", ".post-content", ".c-article-body", "article", "main", "[role=main]"
    );

    private final SourceHttpClient http;

    public String extract(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            return extractFromHtml(http.get(url));
        } catch (Exception e) {
            log.warn("Не удалось скачать статью {}: {}", url, SourceHttpClient.describe(e));
            return null;
        }
    }

    public static String extractFromHtml(String html) {
        if (html == null || html.isBlank()) return null;
        Document doc = Jsoup.parse(html);
        doc.select("script, style, noscript, iframe, svg, header, footer, nav, aside, form, figure, "
                + ".menu, .navigation, .sidebar, .footer, .ads, .advert, .social-share, .share, .related, .comments")
                .remove();

        for (String selector : CONTAINERS) {
            for (Element container : doc.select(selector)) {
                String text = paragraphs(container);
                if (text.length() >= MIN_CHARS) {
                    return TextUtils.truncate(text, MAX_CHARS);
                }
            }
        }
        String text = paragraphs(doc.body());
        return text.length() >= MIN_CHARS ? TextUtils.truncate(text, MAX_CHARS) : null;
    }

    // только абзацы и подзаголовки — без меню, подписей и кнопок
    private static String paragraphs(Element root) {
        if (root == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Element p : root.select("p, h2, h3, li")) {
            String text = p.text().trim();
            boolean isParagraph = p.tagName().equals("p");
            if (isParagraph ? text.length() < 40 : text.length() < 15) continue;
            sb.append(text).append('\n');
        }
        return sb.toString().trim();
    }
}
