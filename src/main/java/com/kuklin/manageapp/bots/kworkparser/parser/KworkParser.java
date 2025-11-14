package com.kuklin.manageapp.bots.kworkparser.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.kworkparser.models.KworkDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
public class KworkParser {
    private static final String ARR_NOT_FOUND = "Не удалось найти массив \"wants\" в файле ";
    public List<KworkDto> getKworksOrNull(String url) {

        Document doc = null;
        try {
            doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Upgrade-Insecure-Requests", "1")
                    .timeout(15_000)
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .get();

        } catch (IOException e) {
            log.error("Не удалось получить страницу для парсинга");
            return null;
        }
        String raw = doc.toString();

        // 1) Найдём массив wants [...] в «грязном» HTML/тексте
        String wantsJson = extractJsonArrayByField(raw, "\"wants\"");
        if (wantsJson == null) {
            log.error(ARR_NOT_FOUND);
            return null;
        }

        // 2) Парсинг в DTO
        ObjectMapper om = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<KworkDto> kworks = null;
        try {
            kworks = om.readValue(wantsJson, new TypeReference<List<KworkDto>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("Не удалось прочитать сущности");
            return null;
        }

        return kworks;
    }

    /*
    Находит внутри текста чистый JSON-массив
     */
    private static String extractJsonArrayByField(String source, String fieldName) {
        int fieldPos = source.indexOf(fieldName + ":");
        if (fieldPos < 0) fieldPos = source.indexOf(fieldName + " :"); // на всякий случай
        if (fieldPos < 0) return null;

        // найдём первую '[' после "wants":
        int start = source.indexOf('[', fieldPos);
        if (start < 0) return null;

        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);

            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '\"') {
                    inString = false;
                }
            } else {
                if (c == '\"') {
                    inString = true;
                } else if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        return source.substring(start, i + 1);
                    }
                }
            }
        }
        return null; // не нашли закрывающую скобку
    }
}



