package com.kuklin.manageapp.common.telegram.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisabledBotsFilterTest {

    private static final String AVIA_PACKAGE = "com.kuklin.manageapp.bots.deparrbot";
    private static final String CALORIE_PACKAGE = "com.kuklin.manageapp.bots.caloriebot";

    @Test
    void disabledIndependentBotHasNoBeans() {
        assertThat(scan(AVIA_PACKAGE, "HH_BOT, AVIA_BOT")).isZero();
    }

    @Test
    void enabledBotIsScanned() {
        assertThat(scan(AVIA_PACKAGE, "HH_BOT")).isPositive();
        assertThat(scan(AVIA_PACKAGE, "")).isPositive();
    }

    @Test
    void calorieBotIsNeverExcluded() {
        // калорийный бот выключается «мягко» (EnabledBots): от его бинов зависят другие модули
        assertThat(scan(CALORIE_PACKAGE, "CALORIE_BOT,METRICS")).isPositive();
    }

    @Test
    void unknownBotNameFailsStartup() {
        assertThatThrownBy(() -> makeFilter("AVIA_BOTT")).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Фильтр можно применять только к боту, чьи классы никто не импортирует снаружи его пакета:
     * иначе при выключении бота контекст не поднимется («No qualifying bean»).
     * Тест падает, если такая зависимость появилась, — тогда бота надо убрать из BOT_PACKAGES
     * или развязать зависимость.
     */
    @Test
    void excludableBotsAreNotImportedFromOutside() throws IOException {
        Path sources = Path.of("src/main/java");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sources)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                String path = sources.relativize(file).toString().replace('\\', '/');
                String code = read(file);
                for (String botPackage : DisabledBotsFilter.BOT_PACKAGES.keySet()) {
                    boolean insideBot = path.startsWith("com/kuklin/manageapp/bots/" + botPackage + "/");
                    // И полные имена в коде, и import static — не только обычный import
                    if (!insideBot && code.contains(DisabledBotsFilter.BOTS_PACKAGE + botPackage + ".")) {
                        violations.add(path + " -> " + botPackage);
                    }
                }
            });
        }
        assertThat(violations).isEmpty();
    }

    private static int scan(String basePackage, String disabledBots) {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        scanner.addExcludeFilter(makeFilter(disabledBots));
        return scanner.findCandidateComponents(basePackage).size();
    }

    private static DisabledBotsFilter makeFilter(String disabledBots) {
        DisabledBotsFilter filter = new DisabledBotsFilter();
        filter.setEnvironment(new MockEnvironment().withProperty("bots.disabled", disabledBots));
        return filter;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
