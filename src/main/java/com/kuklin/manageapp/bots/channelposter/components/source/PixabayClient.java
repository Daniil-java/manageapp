package com.kuklin.manageapp.bots.channelposter.components.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Поиск фото на Pixabay (pixabay.com/api/docs): бесплатно, лицензия разрешает публикацию без указания автора.
 * Автора и Pixabay всё равно подписываем — правила API просят показывать, откуда фото.
 * Ссылки на картинки временные (сутки): Telegram и распознавание забирают фото сразу, мы их не храним.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PixabayClient {

    static final String SEARCH_URL = "https://pixabay.com/api/";
    private static final int PER_PAGE = 20;
    // лимит Pixabay на длину запроса
    private static final int MAX_QUERY = 100;

    private final SourceHttpClient http;
    private final ChannelPosterBotKeyComponent keys;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param imageUrl картинка до 1280 px — для Telegram и распознавания
     * @param pageUrl  страница фото на Pixabay — для подписи автора
     */
    public record StockPhoto(long id, String imageUrl, String pageUrl, String author) {
    }

    /**
     * Горизонтальные фото еды по запросу на русском.
     *
     * @return фото или пустой список, если ничего не нашлось
     */
    public List<StockPhoto> search(String query) throws IOException {
        String q = query.length() > MAX_QUERY ? query.substring(0, MAX_QUERY) : query;
        String url = SEARCH_URL + "?key=" + URLEncoder.encode(keys.getPixabayApiKey(), StandardCharsets.UTF_8)
                + "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&lang=ru&image_type=photo&orientation=horizontal&category=food&safesearch=true&per_page=" + PER_PAGE;
        String json;
        try {
            json = http.get(url, SourceHttpClient.BROWSER_USER_AGENT, Map.of());
        } catch (IOException e) {
            // в тексте ошибки Jsoup — адрес запроса вместе с ключом; наружу только «HTTP 429», «timeout»…
            throw new IOException("Pixabay: " + SourceHttpClient.describe(e));
        }
        return parse(json);
    }

    List<StockPhoto> parse(String json) throws IOException {
        List<StockPhoto> photos = new ArrayList<>();
        for (JsonNode node : objectMapper.readTree(json).path("hits")) {
            String image = node.path("largeImageURL").asText(null);
            if (image == null || image.isBlank()) continue;
            photos.add(new StockPhoto(
                    node.path("id").asLong(),
                    image,
                    node.path("pageURL").asText(null),
                    node.path("user").asText(null)));
        }
        return photos;
    }
}
