package com.kuklin.manageapp.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * ObjectMapper для тестов без Spring-контекста — с теми же настройками, что бин Spring Boot в приложении:
 * java.time, даты строкой ISO, неизвестные поля не роняют разбор.
 */
public final class TestJson {
    // Даты строкой ISO Spring Boot включает сам (JacksonAutoConfiguration), голый builder — нет
    public static final ObjectMapper MAPPER = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .build();

    private TestJson() {
    }
}
