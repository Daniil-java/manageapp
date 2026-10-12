package com.kuklin.manageapp.bots.caloriebot.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Счётчик непереведённых текстов в коде Telegram-части калорийного бота: строки кода,
 * где в строковом литерале (или текстовом блоке """) есть русские буквы. Комментарии не считаются.
 * <p>
 * Число может только уменьшаться: новый текст — сразу в словарь i18n/calorie/bot_*.
 * Перевели пачку обработчиков — тест упадёт и подскажет новое число, его вписываем в EXPECTED.
 * Цель — 0.
 */
class HardcodedTextsCountTest {

    private static final Path TELEGRAM_SOURCES = Path.of("src/main/java/com/kuklin/manageapp/bots/caloriebot/telegram");
    private static final int EXPECTED = 87;

    @Test
    void hardcodedTextsOnlyDecrease() throws IOException {
        Map<String, Integer> byFile = countByFile();
        int total = byFile.values().stream().mapToInt(Integer::intValue).sum();

        assertThat(total)
                .as("Строк с русским текстом в коде бота: %d, записано в тесте: %d.%n"
                        + "Больше — новый текст надо положить в словарь bot_*. Меньше — впиши %d в EXPECTED.%n"
                        + "По файлам: %s", total, EXPECTED, total, byFile)
                .isEqualTo(EXPECTED);
    }

    private static Map<String, Integer> countByFile() throws IOException {
        Map<String, Integer> byFile = new TreeMap<>();
        try (Stream<Path> files = Files.walk(TELEGRAM_SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                int count = countLinesWithRussianText(Files.readString(file, StandardCharsets.UTF_8));
                if (count > 0) byFile.put(TELEGRAM_SOURCES.relativize(file).toString().replace('\\', '/'), count);
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
