package com.kuklin.manageapp.bots.caloriebot.support.telegram;

import com.kuklin.manageapp.bots.caloriebot.support.SupportProperties;
import com.kuklin.manageapp.bots.caloriebot.support.SupportTicket;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.support.SupportGroupService;
import com.kuklin.manageapp.bots.caloriebot.support.SupportTicketService;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.UpdateHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Сообщение пользователя в заявку поддержки — вместо распознавания еды. Фасад отдаёт сюда сообщение, если
 * после кнопки темы / «✍️ Ответить» ждём текст (calorie.support.input-timeout) или если это ответ (reply)
 * на сообщение поддержки. Команда или кнопка меню ожидание сбрасывает и идёт своим обработчиком.
 * Принимаем текст и фото; голосовые и файлы — нет.
 * <p>
 * Не регистрируется в фасаде по команде — фасад спрашивает {@link #isSupportInput} сам.
 */
@Component
@RequiredArgsConstructor
public class SupportInputUpdateHandler implements UpdateHandler {
    private static final String COMMAND_PREFIX = "/";

    private final SupportProperties supportProperties;
    private final SupportTicketService supportTicketService;
    private final SupportGroupService supportGroupService;
    private final CalorieMenuButtons menuButtons;
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;

    public boolean isSupportInput(Update update, TelegramUser telegramUser) {
        if (!supportProperties.isEnabled() || !update.hasMessage()) return false;
        Message message = update.getMessage();
        if (message.hasSuccessfulPayment() || message.getWebAppData() != null) return false;

        Long appUserId = telegramUser.getAppUserId();
        SupportTicket awaiting = supportTicketService.findAwaitingOrNull(appUserId);
        String text = message.getText();
        if (text != null && (text.startsWith(COMMAND_PREFIX) || menuButtons.findCommand(text).isPresent())) {
            if (awaiting != null) supportTicketService.clearAwaiting(appUserId);
            return false;
        }
        return awaiting != null || findTicketByReplyOrNull(message, appUserId) != null;
    }

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        Long appUserId = telegramUser.getAppUserId();
        SupportTicket ticket = supportTicketService.findAwaitingOrNull(appUserId);
        if (ticket == null) ticket = findTicketByReplyOrNull(message, appUserId);
        if (ticket == null) return;

        String ticketNumber = String.valueOf(ticket.getId());
        if (ticket.getStatus() == SupportTicket.Status.CLOSED) {
            calorieTelegramBot.sendReturnedMessage(chatId,
                    texts.get(appUserId, BotTextKeys.SUPPORT_TICKET_CLOSED, ticketNumber));
            return;
        }
        // Ожидание не сбрасываем — можно прислать текст следующим сообщением
        if (!message.hasText() && !message.hasPhoto()) {
            calorieTelegramBot.sendReturnedMessage(chatId, texts.get(appUserId, BotTextKeys.SUPPORT_UNSUPPORTED_CONTENT));
            return;
        }

        boolean created = supportGroupService.submitUserMessage(ticket, telegramUser, message);
        calorieTelegramBot.sendReturnedMessage(chatId, created
                        ? texts.get(appUserId, BotTextKeys.SUPPORT_TICKET_CREATED, ticketNumber)
                        : texts.get(appUserId, BotTextKeys.SUPPORT_MESSAGE_SENT, ticketNumber),
                menuButtons.buildKeyboard(appUserId), null);
    }

    private SupportTicket findTicketByReplyOrNull(Message message, Long appUserId) {
        Message replyTo = message.getReplyToMessage();
        if (replyTo == null || replyTo.getFrom() == null || !Boolean.TRUE.equals(replyTo.getFrom().getIsBot())) return null;
        return supportTicketService.findByReplyOrNull(appUserId, replyTo.getMessageId());
    }

    @Override
    public String getHandlerListName() {
        return SupportInputUpdateHandler.class.getSimpleName();
    }
}
