package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.common.configurations.EnabledBots;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalorieBotCommandsPublisherTest {

    private CalorieTelegramBot bot;
    private CalorieTexts texts;

    @BeforeEach
    void setUp() {
        bot = mock(CalorieTelegramBot.class);
        texts = new CalorieTexts(new I18nProperties(), mock(UserSettingsService.class));
    }

    @Test
    void requestPerLanguageAndDefault() {
        List<SetMyCommands> requests = buildPublisher(Set.of()).buildRequests();

        assertThat(requests).extracting(SetMyCommands::getLanguageCode).containsExactly(null, "ru", "en");
        // Без языка — на языке бота по умолчанию (ru)
        assertThat(getCommand(requests.get(0), "menu").getDescription()).isEqualTo("Меню бота");
        assertThat(getCommand(requests.get(1), "menu").getDescription()).isEqualTo("Меню бота");
        assertThat(getCommand(requests.get(2), "menu").getDescription()).isEqualTo("Bot menu");
    }

    /** Telegram примет только имя из a-z, 0-9, _ длиной до 32 и описание до 256 символов. */
    @Test
    void commandsAreValidForTelegram() {
        for (SetMyCommands request : buildPublisher(Set.of()).buildRequests()) {
            assertThat(request.getCommands()).extracting(BotCommand::getCommand)
                    .containsExactly("menu", "keyboard", "lang", "support", "terms", "privacy")
                    .allMatch(name -> name.matches("[a-z0-9_]{1,32}"));
            assertThat(request.getCommands()).extracting(BotCommand::getDescription)
                    .allMatch(description -> !description.isBlank() && description.length() <= 256);
        }
    }

    @Test
    void publishesAllRequests() throws TelegramApiException {
        buildPublisher(Set.of()).publishCommands();

        verify(bot, times(3)).execute(any(SetMyCommands.class));
    }

    @Test
    void telegramErrorDoesNotStopOthers() throws TelegramApiException {
        when(bot.execute(any(SetMyCommands.class))).thenThrow(new TelegramApiException("boom"));

        buildPublisher(Set.of()).publishCommands();

        verify(bot, times(3)).execute(any(SetMyCommands.class));
    }

    @Test
    void disabledBotPublishesNothing() throws TelegramApiException {
        buildPublisher(Set.of(BotIdentifier.CALORIE_BOT)).publishCommands();

        verify(bot, never()).execute(any(SetMyCommands.class));
    }

    private CalorieBotCommandsPublisher buildPublisher(Set<BotIdentifier> disabled) {
        return new CalorieBotCommandsPublisher(bot, new EnabledBots(disabled), texts, new I18nProperties());
    }

    private static BotCommand getCommand(SetMyCommands request, String name) {
        return request.getCommands().stream().filter(c -> c.getCommand().equals(name)).findFirst().orElseThrow();
    }
}
