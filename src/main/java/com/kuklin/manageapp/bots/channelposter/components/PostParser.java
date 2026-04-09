package com.kuklin.manageapp.bots.channelposter.components;

import com.kuklin.manageapp.bots.channelposter.entities.parser.RedditPost;
import com.kuklin.manageapp.bots.channelposter.entities.parser.Subreddit;
import com.kuklin.manageapp.bots.channelposter.model.RedditPostDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class PostParser {

    // Набор user-agent'ов, чтобы не палиться как бот
    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/17.0 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
    );

    public String parseContent(RedditPost post) {

        if (post == null || post.getUrl() == null) {
            return null;
        }

        // если это не текстовый пост — просто возвращаем ссылку
        if (post.getContentType() != RedditPost.ContentType.TEXT) {
            return post.getUrl();
        }

        try {
            // получаем страницу поста (через comments ссылку)
            String postPageUrl = buildPostPageUrl(post.getUrl());

            Document doc = fetchWithRetry(postPageUrl, 2);

            // основной текст поста
            Element textEl = doc.selectFirst("div.usertext-body div.md");

            if (textEl != null) {
                return textEl.text();
            }

        } catch (Exception ignored) {}

        return null;
    }

    private String buildPostPageUrl(String url) {

        // если уже ссылка на reddit пост — нормализуем
        if (url.contains("reddit.com")) {
            return url.replace("https://www.reddit.com", "https://old.reddit.com")
                    .replace("https://reddit.com", "https://old.reddit.com");
        }

        // если это внешняя ссылка — у нас нет прямого пути к тексту
        // тогда контент = сама ссылка
        return url;
    }

    /**
     * Основной метод:
     * - грузит страницу сабреддита
     * - парсит посты
     */
    public List<RedditPostDto> parseSubreddit(Subreddit subreddit) {

        Document doc = fetchWithRetry(subreddit.getUrl(), 3);
        return extractPosts(doc, subreddit.getId());
    }

    // ================= FETCH =================

    /**
     * Пытается загрузить страницу несколько раз
     * - случайный user-agent
     * - задержка между запросами
     */
    private Document fetchWithRetry(String url, int attempts) {
        for (int i = 0; i < attempts; i++) {
            try {
                sleepRandom();

                return Jsoup.connect(url)
                        .userAgent(randomUserAgent())
                        .timeout(10_000)
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .get();

            } catch (IOException e) {
                // если последняя попытка — кидаем ошибку
                if (i == attempts - 1) {
                    log.warn(getClass().getSimpleName() + " Can't load " + url, e);
                    throw new RuntimeException("Не удалось загрузить: " + url, e);
                }
            }
        }
        throw new RuntimeException("unreachable");
    }

    /**
     * Случайный user-agent из списка
     */
    private String randomUserAgent() {
        return USER_AGENTS.get(ThreadLocalRandom.current().nextInt(USER_AGENTS.size()));
    }

    /**
     * Рандомная задержка (анти-бан)
     */
    private void sleepRandom() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(800, 2000));
        } catch (InterruptedException ignored) {}
    }

    // ================= PARSE =================

    /**
     * Извлекает список постов со страницы сабреддита
     */
    private List<RedditPostDto> extractPosts(Document doc, Long subredditId) {
        List<RedditPostDto> result = new ArrayList<>();

        // каждый пост — div с классом thing
        Elements posts = doc.select("div.thing");

        for (Element post : posts) {

            // у нормального поста есть data-fullname (t3_xxx)
            // реклама и мусор — без него
            if (!post.hasAttr("data-fullname")) continue;

            String redditId = post.attr("data-fullname");

            // заголовок и ссылка
            Element titleEl = post.selectFirst("p.title > a");
            if (titleEl == null) continue;

            String title = titleEl.text();
            String url = titleEl.absUrl("href");

            // автор (может быть null или [deleted])
            String author = textOrNull(post.selectFirst(".author"));

            // строка вида "123 comments"
            Integer commentsCount = parseComments(
                    textOrNull(post.selectFirst("a.comments"))
            );

            // рейтинг поста (иногда скрыт)
            Integer score = parseScore(post);

            // время создания поста
            Instant created = parseCreated(post);

            // тип контента (картинка/видео/ссылка/текст)
            RedditPost.ContentType contentType = detectType(url);

            // собираем сущность
            RedditPostDto dto = new RedditPostDto()
                    .setRedditId(redditId)
                    .setSubredditId(subredditId)
                    .setTitle(title)
                    .setUrl(url)
                    .setAuthor(author)
                    .setCommentsCount(commentsCount)
                    .setScore(score)
                    .setPostCreated(created)
                    .setContentType(contentType);

            result.add(dto);
        }

        return result;
    }

    // ================= HELPERS =================

    /**
     * Безопасно достаёт текст из элемента
     */
    private String textOrNull(Element el) {
        return el != null ? el.text() : null;
    }

    /**
     * Парсит количество комментариев из строки
     * "123 comments" -> 123
     */
    private Integer parseComments(String text) {
        if (text == null) return 0;

        try {
            if (text.contains("comment")) {
                return Integer.parseInt(text.split(" ")[0]);
            }
        } catch (Exception ignored) {}

        return 0;
    }

    /**
     * Парсит score поста
     * Реальное значение лежит в атрибуте title
     */
    private Integer parseScore(Element post) {
        Element scoreEl = post.selectFirst(".score.unvoted");
        if (scoreEl == null) return null;

        String score = scoreEl.attr("title");

        try {
            return Integer.parseInt(score);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Парсит дату создания поста
     */
    private Instant parseCreated(Element post) {
        Element timeEl = post.selectFirst("time");
        if (timeEl == null) return null;

        try {
            return Instant.parse(timeEl.attr("datetime"));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Определяет тип контента по ссылке
     */
    private RedditPost.ContentType detectType(String url) {
        if (url == null) return RedditPost.ContentType.TEXT;

        if (url.contains("i.redd.it") || url.endsWith(".jpg") || url.endsWith(".png")) {
            return RedditPost.ContentType.IMAGE;
        }

        if (url.contains("youtube") || url.contains("youtu.be")) {
            return RedditPost.ContentType.VIDEO;
        }

        if (url.contains("reddit.com")) {
            return RedditPost.ContentType.TEXT;
        }

        return RedditPost.ContentType.LINK;
    }
}
