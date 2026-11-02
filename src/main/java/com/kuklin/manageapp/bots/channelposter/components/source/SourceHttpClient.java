package com.kuklin.manageapp.bots.channelposter.components.source;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Загрузка лент и страниц для источников канала.
 * 4xx не повторяем (доступ закрыт — повтор не поможет), сетевые сбои и 5xx — до 2 попыток.
 */
@Component
@Slf4j
public class SourceHttpClient {

    static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 20_000;
    private static final int MAX_BODY_BYTES = 5 * 1024 * 1024;
    private static final int ATTEMPTS = 2;

    public String get(String url) throws IOException {
        return get(url, BROWSER_USER_AGENT, Map.of());
    }

    public String get(String url, String userAgent, Map<String, String> headers) throws IOException {
        return execute(Jsoup.connect(url)
                .userAgent(userAgent)
                .headers(headers)
                .method(Connection.Method.GET));
    }

    public String postForm(String url, String userAgent, Map<String, String> headers, Map<String, String> form)
            throws IOException {
        return execute(Jsoup.connect(url)
                .userAgent(userAgent)
                .headers(headers)
                .data(form)
                .method(Connection.Method.POST));
    }

    private String execute(Connection connection) throws IOException {
        connection
                .timeout(TIMEOUT_MS)
                .maxBodySize(MAX_BODY_BYTES)
                .ignoreContentType(true)
                .followRedirects(true)
                .header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8");

        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return connection.execute().body();
            } catch (HttpStatusException e) {
                if (e.getStatusCode() < 500) {
                    throw e;
                }
                last = e;
            } catch (IOException e) {
                last = e;
            }
            sleep(1500L * attempt);
        }
        throw last;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Короткое описание ошибки для админа: «HTTP 403», «timeout» и т.п.
     */
    public static String describe(Exception e) {
        if (e instanceof HttpStatusException h) {
            return "HTTP " + h.getStatusCode();
        }
        String msg = e.getMessage();
        String text = e.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
        return text.length() > 300 ? text.substring(0, 300) : text;
    }
}
