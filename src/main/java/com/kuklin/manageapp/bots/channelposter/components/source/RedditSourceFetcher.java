package com.kuklin.manageapp.bots.channelposter.components.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.channelposter.entities.source.ContentSource;
import com.kuklin.manageapp.bots.channelposter.model.source.FetchedItem;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reddit. Старый HTML-парсер old.reddit.com больше не работает: без логина Reddit редиректит на /login.
 * Основной путь — официальный API (app-only OAuth): нужны CHANNELPOSTER_REDDIT_CLIENT_ID и
 * CHANNELPOSTER_REDDIT_CLIENT_SECRET (приложение типа «script» на reddit.com/prefs/apps).
 * Без ключей пробуем публичную RSS-ленту — с серверных IP она часто закрыта.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedditSourceFetcher implements SourceFetcher {

    private static final String USER_AGENT = "server:manageapp-channelposter:v1.0";
    // посты с меньшим рейтингом за сутки — обычно вопросы новичков, на AI их не тратим
    static final int MIN_SCORE = 20;
    private static final Set<String> MEDIA_HINTS = Set.of("image", "hosted:video", "rich:video");
    private static final Set<String> MEDIA_DOMAINS = Set.of("i.redd.it", "v.redd.it", "i.imgur.com", "youtube.com", "youtu.be");

    private final SourceHttpClient http;
    private final ChannelPosterBotKeyComponent keys;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    @Override
    public List<FetchedItem> fetch(ContentSource source) throws Exception {
        String sub = normalizeSubreddit(source.getAddress());
        int limit = source.getMaxItems() == null ? 15 : source.getMaxItems();

        if (keys.hasRedditCredentials()) {
            String json = http.get(
                    "https://oauth.reddit.com/r/" + sub + "/top?t=day&raw_json=1&limit=" + Math.min(limit * 2, 100),
                    USER_AGENT,
                    Map.of("Authorization", "Bearer " + token()));
            return limit(parseListing(json), limit);
        }

        String xml = http.get("https://www.reddit.com/r/" + sub + "/top/.rss?t=day", USER_AGENT, Map.of());
        List<FetchedItem> items = RssSourceFetcher.parseFeed(xml, limit);
        if (items.isEmpty() && !RssSourceFetcher.looksLikeFeed(xml)) {
            throw new IllegalStateException("Reddit не отдал ленту (нужны ключи API Reddit)");
        }
        items.forEach(i -> i.setExternalId("reddit:" + i.getExternalId()));
        return items;
    }

    @Override
    public ContentSource.SourceType supportedType() {
        return ContentSource.SourceType.REDDIT;
    }

    private synchronized String token() throws Exception {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiresAt)) {
            return accessToken;
        }
        String basic = Base64.getEncoder().encodeToString(
                (keys.getRedditClientId() + ":" + keys.getRedditClientSecret()).getBytes(StandardCharsets.UTF_8));
        String json = http.postForm(
                "https://www.reddit.com/api/v1/access_token",
                USER_AGENT,
                Map.of("Authorization", "Basic " + basic),
                Map.of("grant_type", "client_credentials"));
        JsonNode node = objectMapper.readTree(json);
        if (!node.hasNonNull("access_token")) {
            throw new IllegalStateException("Reddit OAuth: " + TextUtils.truncate(json, 200));
        }
        accessToken = node.get("access_token").asText();
        accessTokenExpiresAt = Instant.now().plusSeconds(node.path("expires_in").asLong(3600) - 60);
        return accessToken;
    }

    /**
     * Ответ /top в JSON → материалы. Отсекаем закреплённые, NSFW, картинки/видео и слабые по рейтингу.
     */
    public List<FetchedItem> parseListing(String json) throws Exception {
        List<FetchedItem> result = new ArrayList<>();
        for (JsonNode child : objectMapper.readTree(json).path("data").path("children")) {
            JsonNode d = child.path("data");
            if (d.path("stickied").asBoolean() || d.path("over_18").asBoolean() || d.path("is_video").asBoolean()) continue;
            if (MEDIA_HINTS.contains(d.path("post_hint").asText())) continue;
            if (MEDIA_DOMAINS.contains(d.path("domain").asText())) continue;
            if (d.path("score").asInt() < MIN_SCORE) continue;

            boolean isSelf = d.path("is_self").asBoolean();
            String selftext = d.path("selftext").asText("").trim();
            if (isSelf && selftext.length() < 200) continue; // вопрос в одну строку — не материал для поста

            String url = isSelf
                    ? "https://www.reddit.com" + d.path("permalink").asText()
                    : d.path("url").asText();

            result.add(new FetchedItem()
                    .setExternalId("reddit:" + d.path("name").asText())
                    .setUrl(url)
                    .setTitle(d.path("title").asText())
                    .setSummary(isSelf ? TextUtils.truncate(selftext, 600) : null)
                    .setContent(isSelf ? selftext : null)
                    .setScore(d.path("score").asInt())
                    .setPublishedAt(Instant.ofEpochSecond(d.path("created_utc").asLong())));
        }
        return result;
    }

    private static List<FetchedItem> limit(List<FetchedItem> items, int limit) {
        return items.size() > limit ? new ArrayList<>(items.subList(0, limit)) : items;
    }

    /**
     * «r/nutrition», «https://old.reddit.com/r/nutrition/» → «nutrition».
     */
    public static String normalizeSubreddit(String address) {
        if (address == null) return null;
        String s = address.trim();
        int idx = s.indexOf("/r/");
        if (idx >= 0) {
            s = s.substring(idx + 3);
        } else if (s.startsWith("r/")) {
            s = s.substring(2);
        }
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        return s.isBlank() ? null : s;
    }
}
