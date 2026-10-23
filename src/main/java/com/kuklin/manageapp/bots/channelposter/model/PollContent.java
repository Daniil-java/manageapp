package com.kuklin.manageapp.bots.channelposter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Опрос от AI. Лимиты Telegram: вопрос до 300 символов, 2–10 вариантов до 100 символов, пояснение квиза до 200.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PollContent(
        @JsonProperty("question") String question,
        @JsonProperty("options") List<String> options,
        @JsonProperty("correct_option_id") Integer correctOptionId,
        @JsonProperty("explanation") String explanation
) {
    public static final int MAX_QUESTION = 300;
    public static final int MAX_OPTION = 100;
    public static final int MAX_EXPLANATION = 200;
    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 10;

    /**
     * Telegram примет такой опрос: квизу нужны правильный вариант в пределах списка и пояснение.
     */
    public boolean isValid(boolean quiz) {
        if (isBlankOrLonger(question, MAX_QUESTION)) return false;
        if (options == null || options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) return false;
        if (options.stream().anyMatch(o -> isBlankOrLonger(o, MAX_OPTION))) return false;
        if (options.stream().map(String::trim).distinct().count() != options.size()) return false;
        if (!quiz) return true;
        return correctOptionId != null && correctOptionId >= 0 && correctOptionId < options.size()
                && !isBlankOrLonger(explanation, MAX_EXPLANATION);
    }

    private static boolean isBlankOrLonger(String s, int max) {
        return s == null || s.isBlank() || s.trim().length() > max;
    }
}
