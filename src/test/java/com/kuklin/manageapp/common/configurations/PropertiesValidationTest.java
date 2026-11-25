package com.kuklin.manageapp.common.configurations;

import com.kuklin.manageapp.bots.aiassistantcalendar.configurations.auth.GoogleOAuthProperties;
import com.kuklin.manageapp.bots.caloriebot.access.AiRateLimitProperties;
import com.kuklin.manageapp.bots.caloriebot.dish.DishLimitsProperties;
import com.kuklin.manageapp.bots.caloriebot.dish.ai.AiInputLimitsProperties;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.support.SupportProperties;
import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Validated на свойствах: настоящий application.yaml проходит проверку, значение вне диапазона — приложение не стартует.
 */
class PropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class)
            // переменные окружения без значения по умолчанию в application.yaml
            .withPropertyValues("GOOGLE_OAUTH_CLIENT_ID=test-client", "GOOGLE_CALENDAR_KEY=");

    @Test
    void realApplicationYamlIsValid() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void outOfRangeValueFailsStartup() {
        runner.withPropertyValues("calorie.dish-limits.per-day=0")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("perDay"));
    }

    @Test
    void missingNestedLimitFailsStartup() {
        runner.withPropertyValues("auth.rate-limit.login.window=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties({
            AiInputLimitsProperties.class, DishLimitsProperties.class, AiRateLimitProperties.class,
            I18nProperties.class, SupportProperties.class, ChannelPosterProperties.class,
            GoogleOAuthProperties.class, AuthProperties.class
    })
    static class Config {
    }
}
