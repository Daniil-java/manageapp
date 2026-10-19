package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.caloriebot.components.services.UtmService;
import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmLink;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieSiteLinks;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.utils.ReportHtml;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final CalorieTexts texts;

    // Константы конфигурации
    private static final String WEB_APP_URL = CalorieSiteLinks.UTM_FORM_URL;
    /** Кто создаёт UTM-ссылки (calorie.utm.admin-telegram-ids). */
    @Value("${calorie.utm.admin-telegram-ids}")
    private Set<Long> adminTelegramIds;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long appUserId = telegramUser.getAppUserId();
        Long chatId = update.hasMessage() ?
                update.getMessage().getChatId() :
                update.getCallbackQuery().getMessage().getChatId();
        boolean isAdmin = adminTelegramIds.contains(telegramUser.getTelegramId());

        // 1. Обработка создания через WebApp (только админы)
        if (isAdmin && update.hasMessage() && update.getMessage().getWebAppData() != null) {
            processUtmCreation(update, telegramUser);
            return;
        }

        // 2. Логика для обычного вызова команды (показать ссылки)
        String userCode = utmService.getOrCreateReferralLink(appUserId);
        String botUrl = buildBotLink(userCode);

        // Админу даем кнопку создания, юзеру — кнопку "Переслать"
        ReplyKeyboard keyboard = isAdmin ? getAdminReplyKeyboard(appUserId) : getReferralButtons(appUserId, botUrl);

        calorieTelegramBot.sendReturnedMessage(
                chatId,
                texts.get(appUserId, BotTextKeys.REFERRAL_TEXT, botUrl),
                keyboard,
                null
        );
    }

    private void processUtmCreation(Update update, TelegramUser telegramUser) {
        try {
            String jsonData = update.getMessage().getWebAppData().getData();
            UtmDto dto = objectMapper.readValue(jsonData, UtmDto.class);

            String code = UtmService.normalizeCode(dto.code());
            UtmService.CodeProblem problem = code == null ? null : utmService.getCodeProblemOrNull(code);
            if (problem != null) {
                calorieTelegramBot.sendReturnedMessage(update.getMessage().getChatId(),
                        texts.get(telegramUser.getAppUserId(), getCodeProblemTextKey(problem), ReportHtml.escapeHtml(code),
                                UtmService.CODE_MIN_LENGTH, UtmService.CODE_MAX_LENGTH));
                return;
            }

            UtmLink link = utmService.saveLink(dto, telegramUser.getAppUserId());
            String fullBotLink = buildBotLink(link.getCode());

            String responseText = texts.get(telegramUser.getAppUserId(), BotTextKeys.ADMIN_UTM_CREATED,
                    link.getTittle(),
                    link.getOwnerType().getType(),
                    link.getSourceUrl(),
                    link.getDescription() != null ? link.getDescription() : "—",
                    fullBotLink
            );

            calorieTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), responseText);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse WebApp JSON: {}", e.getMessage());
        }
    }

    private static String getCodeProblemTextKey(UtmService.CodeProblem problem) {
        return switch (problem) {
            case INVALID -> BotTextKeys.ADMIN_UTM_CODE_INVALID;
            case RESERVED -> BotTextKeys.ADMIN_UTM_CODE_RESERVED;
            case TAKEN -> BotTextKeys.ADMIN_UTM_CODE_TAKEN;
        };
    }

    /**
     * Универсальный метод сборки ссылки
     */
    private String buildBotLink(String code) {
        String botUserName = calorieTelegramBot.getBotUsername().substring(1);
        return "https://t.me/" + botUserName + "?start=" + code;
    }

    private InlineKeyboardMarkup getReferralButtons(Long appUserId, String botUrl) {
        InlineKeyboardButton shareBtn = new InlineKeyboardButton();
        shareBtn.setText(texts.get(appUserId, BotTextKeys.REFERRAL_BUTTON_SHARE));
        shareBtn.setSwitchInlineQuery(texts.get(appUserId, BotTextKeys.REFERRAL_SHARE_TEXT, botUrl));

        return new InlineKeyboardMarkup(List.of(List.of(shareBtn)));
    }

    private ReplyKeyboardMarkup getAdminReplyKeyboard(Long appUserId) {
        KeyboardButton webAppButton = new KeyboardButton();
        webAppButton.setText(texts.get(appUserId, BotTextKeys.ADMIN_UTM_BUTTON_CREATE));
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
            String code,          // свой код ссылки, пусто — сгенерируется partner_...
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