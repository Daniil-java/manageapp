package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.configurations.SupportProperties;
import com.kuklin.manageapp.bots.caloriebot.entities.SupportTicket;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.support.SupportCallbacks;
import com.kuklin.manageapp.bots.caloriebot.support.SupportGroupService;
import com.kuklin.manageapp.bots.caloriebot.support.SupportTicketService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.TelegramCalorieBotFacade;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.ButtonStyle;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /support и /paysupport. Группа поддержки настроена (calorie.support.chat-id) — заявка: тема → описание →
 * тема в группе, ответ придёт сюда же (SupportGroupService). Не настроена — ссылка на аккаунт поддержки, как раньше.
 * Кнопки заявки — callback_data «/support &lt;действие&gt; …» ({@link SupportCallbacks}).
 * Само описание (следующее сообщение) принимает SupportInputUpdateHandler.
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class SupportCalorieUpdateHandler implements CalorieBotUpdateHandler {

    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;
    private final SupportProperties supportProperties;
    private final SupportTicketService supportTicketService;
    private final SupportGroupService supportGroupService;

    private static final String SUPPORT_URL = "tg://user?id=7666281607";
    // «Дописать» — не больше стольких открытых заявок под выбором темы
    private static final int MAX_APPEND_BUTTONS = 3;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long appUserId = telegramUser.getAppUserId();
        if (update.hasCallbackQuery()) {
            if (supportProperties.isEnabled()) handleCallback(update.getCallbackQuery(), appUserId);
            return;
        }
        Long chatId = update.getMessage().getChatId();
        if (!supportProperties.isEnabled()) {
            sendContactLink(chatId, telegramUser);
            return;
        }
        // /paysupport — сразу заявка об оплате
        if (update.getMessage().getText().startsWith(Command.CALORIE_PAY_SUPPORT.getCommandText())) {
            supportTicketService.startDraft(appUserId, SupportTicket.Category.PAYMENT);
            calorieTelegramBot.sendReturnedMessage(chatId,
                    texts.get(appUserId, BotTextKeys.buildSupportPromptKey(SupportTicket.Category.PAYMENT)),
                    buildCancelKeyboard(appUserId), null);
            return;
        }
        calorieTelegramBot.sendReturnedMessage(chatId, texts.get(appUserId, BotTextKeys.SUPPORT_CHOOSE),
                buildCategoryKeyboard(appUserId), null);
    }

    private void handleCallback(CallbackQuery query, Long appUserId) {
        Message message = TelegramBot.getCallbackMessage(query);
        Long chatId = message.getChatId();
        String[] parts = query.getData().split(TelegramBot.DEFAULT_DELIMETER);
        String action = parts.length > 1 ? parts[1] : "";
        switch (action) {
            case SupportCallbacks.CATEGORY -> {
                SupportTicket.Category category = findCategoryOrNull(parts);
                if (category == null) return;
                supportTicketService.startDraft(appUserId, category);
                calorieTelegramBot.sendEditMessage(chatId,
                        texts.get(appUserId, BotTextKeys.buildSupportPromptKey(category)),
                        message.getMessageId(), buildCancelKeyboard(appUserId));
            }
            case SupportCallbacks.APPEND -> {
                Long ticketId = findLongOrNull(parts, 2);
                if (ticketId == null) return;
                // Кнопка под сообщением поддержки — его не трогаем, приглашение отдельным сообщением
                SupportTicket ticket = supportTicketService.startAppendOrNull(appUserId, ticketId);
                calorieTelegramBot.sendReturnedMessage(chatId, ticket == null
                                ? texts.get(appUserId, BotTextKeys.SUPPORT_TICKET_CLOSED, String.valueOf(ticketId))
                                : texts.get(appUserId, BotTextKeys.SUPPORT_PROMPT_APPEND, String.valueOf(ticketId)),
                        ticket == null ? null : buildCancelKeyboard(appUserId), null);
            }
            case SupportCallbacks.CANCEL -> {
                supportTicketService.clearAwaiting(appUserId);
                calorieTelegramBot.sendEditMessage(chatId, texts.get(appUserId, BotTextKeys.SUPPORT_CANCELLED),
                        message.getMessageId(), null);
            }
            case SupportCallbacks.RATE -> {
                Long ticketId = findLongOrNull(parts, 2);
                if (ticketId == null || parts.length < 4) return;
                SupportTicket ticket = supportTicketService.rateOrNull(appUserId, ticketId,
                        SupportCallbacks.RATE_UP.equals(parts[3]));
                if (ticket == null) return;
                calorieTelegramBot.sendEditMessage(chatId, texts.get(appUserId, BotTextKeys.SUPPORT_RATED),
                        message.getMessageId(), null);
                supportGroupService.postRating(ticket);
            }
            default -> log.warn("Unknown support callback: {}", query.getData());
        }
    }

    private InlineKeyboardMarkup buildCategoryKeyboard(Long appUserId) {
        TelegramKeyboard.TelegramKeyboardBuilder keyboard = TelegramKeyboard.builder();
        List<InlineKeyboardButton> buttons = Arrays.stream(SupportTicket.Category.values())
                .map(category -> TelegramKeyboard.button(
                        category.getEmoji() + " " + texts.get(appUserId, BotTextKeys.buildSupportCategoryKey(category)),
                        buildCallback(SupportCallbacks.CATEGORY, category.name())))
                .toList();
        for (int i = 0; i < buttons.size(); i += 2) {
            keyboard.row(new ArrayList<>(buttons.subList(i, Math.min(i + 2, buttons.size()))));
        }
        supportTicketService.getOpenTickets(appUserId).stream()
                .limit(MAX_APPEND_BUTTONS)
                .forEach(ticket -> keyboard.row(TelegramKeyboard.button(
                        texts.get(appUserId, BotTextKeys.SUPPORT_BUTTON_APPEND, String.valueOf(ticket.getId())),
                        buildCallback(SupportCallbacks.APPEND, String.valueOf(ticket.getId())))));
        return keyboard.build();
    }

    private InlineKeyboardMarkup buildCancelKeyboard(Long appUserId) {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(texts.get(appUserId, BotTextKeys.SUPPORT_BUTTON_CANCEL),
                        buildCallback(SupportCallbacks.CANCEL)))
                .build();
    }

    private static String buildCallback(String... parts) {
        return Command.CALORIE_SUPPORT.getCommandText() + TelegramBot.DEFAULT_DELIMETER
                + String.join(TelegramBot.DEFAULT_DELIMETER, parts);
    }

    private static SupportTicket.Category findCategoryOrNull(String[] parts) {
        if (parts.length < 3) return null;
        try {
            return SupportTicket.Category.valueOf(parts[2]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Long findLongOrNull(String[] parts, int index) {
        if (parts.length <= index) return null;
        try {
            return Long.valueOf(parts[index]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Группа поддержки не настроена — ссылка на аккаунт поддержки. */
    private void sendContactLink(Long chatId, TelegramUser telegramUser) {
        Long appUserId = telegramUser.getAppUserId();
        calorieTelegramBot.sendReturnedMessage(
                chatId,
                // ID строкой: число ICU напечатал бы с разделителями разрядов (425 120 436)
                texts.get(appUserId, BotTextKeys.SUPPORT_TEXT, String.valueOf(telegramUser.getTelegramId())),
                TelegramKeyboard.builder()
                        .row(TelegramKeyboard.urlButton(texts.get(appUserId, BotTextKeys.SUPPORT_BUTTON), SUPPORT_URL, ButtonStyle.PRIMARY))
                        .build(),
                null
        );
    }

    // /paysupport обязателен по правилам Telegram для ботов с оплатой; в меню команд не публикуется
    @Autowired
    public void registerPaySupport(TelegramCalorieBotFacade messageFacade) {
        messageFacade.register(Command.CALORIE_PAY_SUPPORT.getCommandText(), this);
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_SUPPORT.getCommandText();
    }
}
