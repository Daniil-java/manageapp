package com.kuklin.manageapp.bots.caloriebot.support;

import com.kuklin.manageapp.bots.caloriebot.components.repository.SupportMessageRepository;
import com.kuklin.manageapp.bots.caloriebot.components.repository.SupportTicketRepository;
import com.kuklin.manageapp.bots.caloriebot.configurations.SupportProperties;
import com.kuklin.manageapp.bots.caloriebot.entities.SupportMessage;
import com.kuklin.manageapp.bots.caloriebot.entities.SupportTicket;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.utils.ReportHtml;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.TelegramBot;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.forum.CloseForumTopic;
import org.telegram.telegrambots.meta.api.methods.forum.CreateForumTopic;
import org.telegram.telegrambots.meta.api.methods.forum.EditForumTopic;
import org.telegram.telegrambots.meta.api.methods.forum.ReopenForumTopic;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageId;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.forum.ForumTopic;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * Пересылка заявок между пользователем и группой поддержки (форумом, calorie.support.chat-id).
 * <p>
 * Первое сообщение заявки создаёт тему: карточка ({@link SupportTicketCardBuilder}) и копия сообщения.
 * Ответ в теме бот пересылает пользователю с кнопкой «✍️ Ответить». В теме: сообщение с «!» в начале —
 * заметка, пользователю не уходит; /close или кнопка «✅ Закрыть» — закрыть заявку.
 * Тексты для пользователя — из словарей bot_*, тексты в группе — по-русски.
 */
@Service
@Slf4j
public class SupportGroupService {
    public static final String CLOSE_CALLBACK = "supclose";
    private static final String CLOSE_COMMAND = "/close";
    private static final String NOTE_PREFIX = "!";
    private static final String PHOTO_PLACEHOLDER = "[фото]";

    @Autowired
    private SupportProperties properties;
    @Autowired
    private SupportTicketRepository ticketRepository;
    @Autowired
    private SupportMessageRepository messageRepository;
    @Autowired
    private SupportTicketCardBuilder cardBuilder;
    @Autowired
    private TelegramUserService telegramUserService;
    @Autowired
    private CalorieTexts texts;
    // Бот сам зависит от сервиса (апдейты из группы) — берём его лениво
    @Lazy
    @Autowired
    private CalorieTelegramBot bot;

    /** Апдейт из группы поддержки — его обрабатывает этот сервис, а не фасад бота. */
    public boolean isSupportGroupUpdate(Update update) {
        if (!properties.isEnabled()) return false;
        Long chatId = update.hasMessage() ? update.getMessage().getChatId()
                : update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null
                ? update.getCallbackQuery().getMessage().getChatId()
                : null;
        return properties.getChatId().equals(chatId);
    }

    public void handleGroupUpdate(Update update) {
        if (update.hasCallbackQuery()) {
            handleGroupCallback(update.getCallbackQuery());
            return;
        }
        Message message = update.getMessage();
        if (message.getFrom() == null || Boolean.TRUE.equals(message.getFrom().getIsBot())) return;
        // Сообщения вне тем (General) и служебные (тема создана, закрыта) не трогаем
        if (message.getMessageThreadId() == null || !isRelayable(message)) return;
        SupportTicket ticket = ticketRepository.findByTopicId(message.getMessageThreadId()).orElse(null);
        if (ticket == null) return;

        String text = message.hasText() ? message.getText().trim() : null;
        if (text != null && (text.equals(CLOSE_COMMAND) || text.startsWith(CLOSE_COMMAND + "@"))) {
            close(ticket);
            return;
        }
        if (text != null && text.startsWith(NOTE_PREFIX)) return;
        relayToUser(ticket, message);
    }

    /**
     * Сообщение пользователя в заявку. Черновик — сначала тема с карточкой. Возвращает true, если заявка
     * только что открыта (пользователю — «заявка принята»), false — сообщение дописано в открытую.
     */
    public boolean submitUserMessage(SupportTicket ticket, TelegramUser user, Message message) {
        boolean created = ticket.getStatus() == SupportTicket.Status.DRAFT;
        if (created) openTopic(ticket, user);

        Integer groupMessageId = copyMessage(properties.getChatId(), ticket.getTopicId(), message.getChatId(),
                message.getMessageId(), null, null);
        messageRepository.save(new SupportMessage()
                .setTicketId(ticket.getId())
                .setDirection(SupportMessage.Direction.USER)
                .setText(getMessageText(message))
                .setUserMessageId(message.getMessageId())
                .setGroupMessageId(groupMessageId));
        ticketRepository.save(ticket
                .setLastUserMessageAt(Instant.now())
                .setAwaitingTextSince(null));
        return created;
    }

    /** Пользователь оценил закрытую заявку — оценка в теме. */
    public void postRating(SupportTicket ticket) {
        sendToTopic(ticket.getTopicId(), "Оценка: " + (ticket.getRating() > 0 ? "👍" : "👎"), null);
    }

    /** Напоминание в темах, где пользователь ждёт ответа дольше calorie.support.remind-after. Одно на сообщение. */
    public void remindUnanswered() {
        if (!properties.isEnabled()) return;
        Instant now = Instant.now();
        for (SupportTicket ticket : ticketRepository.findUnanswered(now.minus(properties.getRemindAfter()))) {
            long hours = Duration.between(ticket.getLastUserMessageAt(), now).toHours();
            sendToTopic(ticket.getTopicId(), "⏰ Пользователь ждёт ответа " + hours + " ч", null);
            ticketRepository.save(ticket.setRemindedAt(now));
        }
    }

    private void openTopic(SupportTicket ticket, TelegramUser user) {
        ForumTopic topic = execute(CreateForumTopic.builder()
                .chatId(properties.getChatId())
                .name(cardBuilder.buildTopicName(ticket, user))
                .build());
        if (topic == null) {
            throw new IllegalStateException("Support topic not created for ticket " + ticket.getId());
        }
        ticketRepository.save(ticket
                .setTopicId(topic.getMessageThreadId())
                .setStatus(SupportTicket.Status.OPEN));
        sendToTopic(ticket.getTopicId(), cardBuilder.buildCard(ticket, user), buildCloseKeyboard(ticket));
    }

    private void relayToUser(SupportTicket ticket, Message message) {
        TelegramUser user = findUserOrNull(ticket);
        if (user == null) {
            sendToTopic(ticket.getTopicId(), "⚠️ Пользователь не найден — не отправлено", null);
            return;
        }
        if (ticket.getStatus() == SupportTicket.Status.CLOSED) reopen(ticket, user);

        Long appUserId = ticket.getAppUserId();
        String header = texts.get(appUserId, BotTextKeys.SUPPORT_REPLY_HEADER, String.valueOf(ticket.getId()));
        InlineKeyboardMarkup keyboard = TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(texts.get(appUserId, BotTextKeys.SUPPORT_BUTTON_REPLY),
                        Command.CALORIE_SUPPORT.getCommandText() + TelegramBot.DEFAULT_DELIMETER
                                + SupportCallbacks.APPEND + TelegramBot.DEFAULT_DELIMETER + ticket.getId()))
                .build();

        Integer userMessageId;
        if (message.hasText()) {
            Message sent = bot.sendReturnedMessage(user.getTelegramId(),
                    header + "\n\n" + ReportHtml.escapeHtml(message.getText()), keyboard, null);
            userMessageId = sent == null ? null : sent.getMessageId();
        } else {
            // Фото, файл, стикер — копией; подпись (если у типа она есть) — с заголовком
            String caption = hasCaption(message)
                    ? header + (message.getCaption() == null ? "" : "\n\n" + ReportHtml.escapeHtml(message.getCaption()))
                    : null;
            userMessageId = copyMessage(user.getTelegramId(), null, message.getChatId(), message.getMessageId(),
                    caption, keyboard);
        }
        if (userMessageId == null) {
            sendToTopic(ticket.getTopicId(), "⚠️ Не доставлено — возможно, пользователь заблокировал бота", null);
            return;
        }
        messageRepository.save(new SupportMessage()
                .setTicketId(ticket.getId())
                .setDirection(SupportMessage.Direction.SUPPORT)
                .setText(getMessageText(message))
                .setUserMessageId(userMessageId)
                .setGroupMessageId(message.getMessageId()));
        ticketRepository.save(ticket.setLastSupportMessageAt(Instant.now()));
    }

    private void handleGroupCallback(CallbackQuery query) {
        String[] parts = query.getData() == null ? new String[0] : query.getData().split(TelegramBot.DEFAULT_DELIMETER);
        if (parts.length < 2 || !CLOSE_CALLBACK.equals(parts[0])) return;
        Long ticketId;
        try {
            ticketId = Long.valueOf(parts[1]);
        } catch (NumberFormatException e) {
            return;
        }
        SupportTicket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null || ticket.getStatus() != SupportTicket.Status.OPEN) return;
        close(ticket);
        Message card = TelegramBot.getCallbackMessage(query);
        bot.sendEditMessageReplyMarkupNull(card.getChatId(), card.getMessageId());
    }

    private void close(SupportTicket ticket) {
        if (ticket.getStatus() != SupportTicket.Status.OPEN) return;
        ticketRepository.save(ticket
                .setStatus(SupportTicket.Status.CLOSED)
                .setClosedAt(Instant.now())
                .setAwaitingTextSince(null));
        TelegramUser user = findUserOrNull(ticket);
        sendToTopic(ticket.getTopicId(), "✅ Заявка закрыта", null);
        if (user != null) {
            renameTopic(ticket, user);
            Long appUserId = ticket.getAppUserId();
            String rate = Command.CALORIE_SUPPORT.getCommandText() + TelegramBot.DEFAULT_DELIMETER
                    + SupportCallbacks.RATE + TelegramBot.DEFAULT_DELIMETER + ticket.getId() + TelegramBot.DEFAULT_DELIMETER;
            bot.sendReturnedMessage(user.getTelegramId(),
                    texts.get(appUserId, BotTextKeys.SUPPORT_CLOSED_NOTICE, String.valueOf(ticket.getId())),
                    TelegramKeyboard.builder()
                            .row(TelegramKeyboard.button(texts.get(appUserId, BotTextKeys.SUPPORT_BUTTON_RATE_UP),
                                            rate + SupportCallbacks.RATE_UP),
                                    TelegramKeyboard.button(texts.get(appUserId, BotTextKeys.SUPPORT_BUTTON_RATE_DOWN),
                                            rate + SupportCallbacks.RATE_DOWN))
                            .build(),
                    null);
        }
        execute(CloseForumTopic.builder().chatId(properties.getChatId()).messageThreadId(ticket.getTopicId()).build());
    }

    /** Поддержка написала в закрытую заявку — открываем снова: тема, статус, название. */
    private void reopen(SupportTicket ticket, TelegramUser user) {
        ticketRepository.save(ticket
                .setStatus(SupportTicket.Status.OPEN)
                .setClosedAt(null));
        execute(ReopenForumTopic.builder().chatId(properties.getChatId()).messageThreadId(ticket.getTopicId()).build());
        renameTopic(ticket, user);
    }

    private void renameTopic(SupportTicket ticket, TelegramUser user) {
        execute(EditForumTopic.builder()
                .chatId(properties.getChatId())
                .messageThreadId(ticket.getTopicId())
                .name(cardBuilder.buildTopicName(ticket, user))
                .build());
    }

    private InlineKeyboardMarkup buildCloseKeyboard(SupportTicket ticket) {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button("✅ Закрыть заявку", CLOSE_CALLBACK + TelegramBot.DEFAULT_DELIMETER + ticket.getId()))
                .build();
    }

    private void sendToTopic(Integer topicId, String text, InlineKeyboardMarkup keyboard) {
        bot.sendReturnedMessage(SendMessage.builder()
                .chatId(properties.getChatId())
                .messageThreadId(topicId)
                .text(text)
                .parseMode(ParseMode.HTML)
                .disableWebPagePreview(true)
                .replyMarkup(keyboard)
                .build());
    }

    private Integer copyMessage(Long toChatId, Integer toTopicId, Long fromChatId, Integer messageId,
                                String caption, InlineKeyboardMarkup keyboard) {
        MessageId copied = execute(CopyMessage.builder()
                .chatId(toChatId)
                .messageThreadId(toTopicId)
                .fromChatId(fromChatId)
                .messageId(messageId)
                .caption(caption)
                .parseMode(caption == null ? null : ParseMode.HTML)
                .replyMarkup(keyboard)
                .build());
        return copied == null ? null : copied.getMessageId().intValue();
    }

    private <T extends Serializable> T execute(BotApiMethod<T> method) {
        try {
            return bot.execute(method);
        } catch (TelegramApiException e) {
            log.error("Support group: {} failed: {}", method.getMethod(), e.getMessage());
            return null;
        }
    }

    private TelegramUser findUserOrNull(SupportTicket ticket) {
        return telegramUserService.findByAppUserIdAndBotIdentifier(ticket.getAppUserId(), BotIdentifier.CALORIE_BOT)
                .orElse(null);
    }

    private static boolean isRelayable(Message message) {
        return message.hasText() || message.hasPhoto() || message.hasDocument() || message.hasVideo()
                || message.hasSticker() || message.hasAnimation();
    }

    private static boolean hasCaption(Message message) {
        return message.hasPhoto() || message.hasDocument() || message.hasVideo() || message.hasAnimation();
    }

    private static String getMessageText(Message message) {
        if (message.hasText()) return message.getText();
        if (message.getCaption() != null) return message.getCaption();
        return message.hasPhoto() ? PHOTO_PLACEHOLDER : null;
    }
}
