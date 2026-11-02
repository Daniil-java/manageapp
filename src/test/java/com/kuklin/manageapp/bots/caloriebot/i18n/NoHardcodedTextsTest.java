package com.kuklin.manageapp.bots.caloriebot.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * В коде, который пишет пользователю бота, не должно быть русского текста: строковых литералов
 * (и текстовых блоков """) с русскими буквами. Комментарии не считаются.
 * <p>
 * Проверяем Telegram-часть бота (telegram) и сборку текстов (texts). Новый текст — в словарь
 * i18n/calorie/bot_* и ключ в BotTextKeys, иначе на английском пользователь увидит русский.
 * Остальной код калорийного бота (Swagger-описания, промпты ИИ, валидация DTO) сюда не входит.
 */
class NoHardcodedTextsTest {

    private static final Path CALORIE_SOURCES = Path.of("src/main/java/com/kuklin/manageapp/bots/caloriebot");
    private static final List<Path> CHECKED_SOURCES = List.of(
            CALORIE_SOURCES.resolve("telegram"),
            CALORIE_SOURCES.resolve("texts")
    );

    @Test
    void noRussianTextInBotCode() throws IOException {
        Map<String, Integer> byFile = countByFile();

        assertThat(byFile)
                .as("Русский текст в коде бота (файл → число строк): %s.%n"
                        + "Положи его в словарь i18n/calorie/bot_* и бери через CalorieTexts.", byFile)
                .isEmpty();
    }

    private static Map<String, Integer> countByFile() throws IOException {
        Map<String, Integer> byFile = new TreeMap<>();
        for (Path sources : CHECKED_SOURCES) {
            try (Stream<Path> files = Files.walk(sources)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    int count = countLinesWithRussianText(Files.readString(file, StandardCharsets.UTF_8));
                    if (count > 0) byFile.put(CALORIE_SOURCES.relativize(file).toString().replace('\\', '/'), count);
                }
            }
        }
        return byFile;
    }

    private enum State { CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, CHAR, TEXT_BLOCK }

    /**
     * Проходим исходник посимвольно и помним, где мы: в коде, комментарии, строке или текстовом блоке.
     * Строка кода считается, если на ней есть русская буква внутри строки или текстового блока.
     */
    static int countLinesWithRussianText(String source) {
        State state = State.CODE;
        int count = 0;
        boolean lineCounted = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\n') {
                lineCounted = false;
                if (state == State.LINE_COMMENT) state = State.CODE;
                continue;
            }
            switch (state) {
                case CODE -> {
                    if (source.startsWith("//", i)) {
                        state = State.LINE_COMMENT;
                    } else if (source.startsWith("/*", i)) {
                        state = State.BLOCK_COMMENT;
                        i++;
                    } else if (source.startsWith("\"\"\"", i)) {
                        state = State.TEXT_BLOCK;
                        i += 2;
                    } else if (c == '"') {
                        state = State.STRING;
                    } else if (c == '\'') {
                        state = State.CHAR;
                    }
                }
                case BLOCK_COMMENT -> {
                    if (source.startsWith("*/", i)) {
                        state = State.CODE;
                        i++;
                    }
                }
                case STRING, CHAR, TEXT_BLOCK -> {
                    if (c == '\\') {
                        i++; // экранированный символ (\" \n …) строку не закрывает
                    } else if (state == State.TEXT_BLOCK && source.startsWith("\"\"\"", i)) {
                        state = State.CODE;
                        i += 2;
                    } else if ((state == State.STRING && c == '"') || (state == State.CHAR && c == '\'')) {
                        state = State.CODE;
                    } else if (!lineCounted && Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC) {
                        lineCounted = true;
                        count++;
                    }
                }
                case LINE_COMMENT -> { }
            }
        }
        return count;
    }

    @Test
    void countsOnlyTextsNotComments() {
        String source = """
                // комментарий "в кавычках"
                /* блок
                   "тоже комментарий" */
                String a = "Привет"; // и тут "коммент"
                String b = "hello \\" ещё строка";
                String c = "раз" + "два";
                String d = \"""
                        текст блока
                        second line
                        \""";
                char e = 'ы';
                """;

        assertThat(countLinesWithRussianText(source)).isEqualTo(5); // a, b, c, строка «текст блока», e
    }
}
