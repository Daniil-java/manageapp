package com.kuklin.manageapp.common.telegram.config;

import com.kuklin.manageapp.common.telegram.BotIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Не пускает в Spring-контекст выключенных независимых ботов (bots.disabled, см. {@link EnabledBots}).
 * Работает на этапе сканирования компонентов: класс из пакета такого бота не становится бином вовсе —
 * нет ни обработчиков, ни шедулеров, ни клиентов, ни контроллеров. Подключён в ManageappApplication (@ComponentScan).
 * <p>
 * Участвуют только боты из {@link #BOT_PACKAGES}: их классы никто вне пакета бота не импортирует
 * (за этим следит DisabledBotsFilterTest). Калорийный бот и METRICS здесь сознательно не участвуют —
 * от них зависят другие модули (channelposter, payment, отправка ошибок из LoggingConfig).
 * Они выключаются «мягко»: не регистрируются в Telegram и не запускают шедулеры, но бины живут.
 * <p>
 * JPA-сущности и репозитории сканируются отдельно и остаются — без сервисов бота они ничего не делают.
 */
@Slf4j
public class DisabledBotsFilter implements TypeFilter, EnvironmentAware {

    static final String BOTS_PACKAGE = "com.kuklin.manageapp.bots.";

    /** Пакет в bots/ → бот. Добавлять сюда можно только бота, чьи классы не нужны никому снаружи. */
    static final Map<String, BotIdentifier> BOT_PACKAGES = Map.of(
            "aiassistantcalendar", BotIdentifier.ASSISTANT_BOT,
            "bookingbot", BotIdentifier.BOOKING_BOT,
            "channelposter", BotIdentifier.CHANNEL_POSTER,
            "deparrbot", BotIdentifier.AVIA_BOT,
            "hhparserbot", BotIdentifier.HH_BOT,
            "kworkparser", BotIdentifier.KWORK,
            "nicotinebot", BotIdentifier.NICOTINE_BOT,
            "pomidorotimer", BotIdentifier.POMIDORO_BOT
    );

    private List<String> excludedPackages = List.of();

    @Override
    public void setEnvironment(Environment environment) {
        Set<BotIdentifier> disabled = parseBotIdentifiers(environment.getProperty("bots.disabled", ""));
        excludedPackages = BOT_PACKAGES.entrySet().stream()
                .filter(e -> disabled.contains(e.getValue()))
                .map(e -> BOTS_PACKAGE + e.getKey() + ".")
                .sorted()
                .toList();
        if (!excludedPackages.isEmpty()) {
            log.info("Bots excluded from Spring context (beans not created): {}", excludedPackages);
        }
    }

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
        String className = metadataReader.getClassMetadata().getClassName();
        for (String excludedPackage : excludedPackages) {
            if (className.startsWith(excludedPackage)) return true;
        }
        return false;
    }

    // Тот же формат, что у EnabledBots: имена BotIdentifier через запятую. Неизвестное имя — приложение не стартует
    private static Set<BotIdentifier> parseBotIdentifiers(String value) {
        Set<BotIdentifier> result = EnumSet.noneOf(BotIdentifier.class);
        for (String name : value.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) result.add(BotIdentifier.valueOf(trimmed));
        }
        return result;
    }
}
