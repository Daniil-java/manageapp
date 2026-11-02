package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.common.configurations.EnabledBots;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

/**
 * Список команд «/» калорийного бота — на каждом языке бота, вместо ручной настройки в BotFather.
 * <p>
 * Выставляется при каждом старте: Telegram показывает пользователю список на языке его клиента,
 * клиенту на другом языке — список без языка (на языке бота по умолчанию). Сбой Telegram старт не ломает.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CalorieBotCommandsPublisher {

    /** Команда и ключ её описания в словаре bot_*. */
    private record PublicCommand(Command command, String descriptionKey) {
    }

    /** Команды в списке — по порядку. Служебные (/admin, /utmstats, кнопочные /close …) сюда не попадают. */
    private static final List<PublicCommand> PUBLIC_COMMANDS = List.of(
            new PublicCommand(Command.CALORIE_MENU, BotTextKeys.COMMAND_MENU),
            new PublicCommand(Command.CALORIE_KEYBOARD, BotTextKeys.COMMAND_KEYBOARD),
            new PublicCommand(Command.CALORIE_LANGUAGE, BotTextKeys.COMMAND_LANGUAGE),
            new PublicCommand(Command.CALORIE_SUPPORT, BotTextKeys.COMMAND_SUPPORT),
            new PublicCommand(Command.CALORIE_TERMS, BotTextKeys.COMMAND_TERMS),
            new PublicCommand(Command.CALORIE_PRIVACY, BotTextKeys.COMMAND_PRIVACY)
    );

    private final CalorieTelegramBot calorieTelegramBot;
    private final EnabledBots enabledBots;
    private final CalorieTexts texts;
    private final I18nProperties i18nProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void publishCommands() {
        if (!enabledBots.isEnabled(CalorieTelegramBot.BOT_IDENTIFIER)) return;

        for (SetMyCommands request : buildRequests()) {
            try {
                calorieTelegramBot.execute(request);
            } catch (TelegramApiException e) {
                log.warn("Failed to set bot commands for language {}: {}", request.getLanguageCode(), e.getMessage());
            }
        }
    }

    /** Список без языка (язык бота по умолчанию) и по списку на каждый язык бота. */
    List<SetMyCommands> buildRequests() {
        List<SetMyCommands> requests = new ArrayList<>();
        requests.add(buildRequest(i18nProperties.getDefaultLanguage(), null));
        for (AppLanguage language : AppLanguage.values()) {
            requests.add(buildRequest(language, language.getCode()));
        }
        return requests;
    }

    private SetMyCommands buildRequest(AppLanguage language, String languageCode) {
        List<BotCommand> commands = PUBLIC_COMMANDS.stream()
                // Telegram ждёт имя команды без «/»
                .map(c -> new BotCommand(c.command().getCommandText().substring(1), texts.get(language, c.descriptionKey())))
                .toList();
        return new SetMyCommands(commands, new BotCommandScopeDefault(), languageCode);
    }
}
