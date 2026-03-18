package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.caloriebot.components.services.UtmService;
import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmLink;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class UtmCalorieUpdateHandler implements CalorieBotUpdateHandler {

    private final CalorieTelegramBot calorieTelegramBot;
    private final UtmService utmService;
    private final ObjectMapper objectMapper; // Инжектится из контекста

    // Константы конфигурации
    private static final String WEB_APP_URL = "https://kuklin.dev/calorie/utm-form";
    private static final Set<Long> ADMIN_IDS = Set.of(425120436L, 420478432L);

    // Шаблоны сообщений
    private static final String REFERRAL_TEMPLATE = """
            <b>💎 Твоя реферальная программа</b>
            
            Делись ботом с друзьями! При переходе по ссылке они автоматически станут твоими рефералами.
            
            Твоя персональная ссылка (нажми, чтобы скопировать):
            <code>%s</code>
            """;

    private static final String UTM_SUCCESS_TEMPLATE = """
            ✅ <b>UTM-ссылка успешно создана!</b>
            
            🏷 <b>Название:</b> %s
            👤 <b>Тип:</b> %s
            🌐 <b>Источник:</b> %s
            📝 <b>Описание:</b> %s
            
            🔗 <b>Ваша ссылка:</b>
            <code>%s</code>
            """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasMessage() ?
                update.getMessage().getChatId() :
                update.getCallbackQuery().getMessage().getChatId();
        boolean isAdmin = ADMIN_IDS.contains(telegramUser.getTelegramId());

        // 1. Обработка создания через WebApp (только админы)
        if (isAdmin && update.hasMessage() && update.getMessage().getWebAppData() != null) {
            processUtmCreation(update, telegramUser);
            return;
        }

        // 2. Логика для обычного вызова команды (показать ссылки)
        String userCode = utmService.getOrCreateReferralLink(telegramUser.getTelegramId());
        String botUrl = buildBotLink(userCode);

        // Админу даем кнопку создания, юзеру — кнопку "Переслать"
        ReplyKeyboard keyboard = isAdmin ? getAdminReplyKeyboard() : getReferralButtons(botUrl);

        calorieTelegramBot.sendReturnedMessage(
                chatId,
                String.format(REFERRAL_TEMPLATE, botUrl),
                keyboard,
                null
        );
    }

    private void processUtmCreation(Update update, TelegramUser telegramUser) {
        try {
            String jsonData = update.getMessage().getWebAppData().getData();
            UtmDto dto = objectMapper.readValue(jsonData, UtmDto.class);

            UtmLink link = utmService.saveLink(dto, telegramUser.getTelegramId());
            String fullBotLink = buildBotLink(link.getCode());

            String responseText = UTM_SUCCESS_TEMPLATE.formatted(
                    link.getTittle(),
                    link.getOwnerType().getType(),
                    link.getSourceUrl(),
                    link.getDescription() != null ? link.getDescription() : "—",
                    fullBotLink
            );

            calorieTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), responseText);

        } catch (JsonProcessingException e) {
            log.error("Ошибка парсинга JSON из WebApp: {}", e.getMessage());
        }
    }

    /**
     * Универсальный метод сборки ссылки
     */
    private String buildBotLink(String code) {
        String botUserName = calorieTelegramBot.getBotUsername().substring(1);
        return "https://t.me/" + botUserName + "?start=" + code;
    }

    public InlineKeyboardMarkup getReferralButtons(String botUrl) {
        String shareText = "Привет! Пользуюсь этим ботом для подсчета калорий, очень удобно. Залетай: " + botUrl;

        InlineKeyboardButton shareBtn = new InlineKeyboardButton();
        shareBtn.setText("🚀 Переслать другу");
        shareBtn.setSwitchInlineQuery(shareText);

        return new InlineKeyboardMarkup(List.of(List.of(shareBtn)));
    }

    private ReplyKeyboardMarkup getAdminReplyKeyboard() {
        KeyboardButton webAppButton = new KeyboardButton();
        webAppButton.setText("➕ Создать ссылку для канала");
        webAppButton.setWebApp(new WebAppInfo(WEB_APP_URL));

        KeyboardRow row = new KeyboardRow();
        row.add(webAppButton);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        return markup;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UtmDto(
            String ownerType,
            String tittle,
            String sourceUrl,
            String description,
            Long creatorId
    ) {}

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_UTM.getCommandText();
    }
}