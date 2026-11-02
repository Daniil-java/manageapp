package com.kuklin.manageapp.bots.caloriebot.support;

import com.kuklin.manageapp.bots.caloriebot.components.repository.SupportMessageRepository;
import com.kuklin.manageapp.bots.caloriebot.components.repository.SupportTicketRepository;
import com.kuklin.manageapp.bots.caloriebot.configurations.SupportProperties;
import com.kuklin.manageapp.bots.caloriebot.entities.SupportMessage;
import com.kuklin.manageapp.bots.caloriebot.entities.SupportTicket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Состояние заявок в поддержку — без Telegram: черновик, ожидание сообщения пользователя, оценка.
 * Пересылку в группу поддержки и обратно делает {@link SupportGroupService}.
 */
@Service
@RequiredArgsConstructor
public class SupportTicketService {
    private final SupportTicketRepository ticketRepository;
    private final SupportMessageRepository messageRepository;
    private final SupportProperties properties;

    /** Заявка, в которую уходит следующее сообщение пользователя; null — сообщение обычное (еда, команда). */
    public SupportTicket findAwaitingOrNull(Long appUserId) {
        Instant since = Instant.now().minus(properties.getInputTimeout());
        return ticketRepository
                .findFirstByAppUserIdAndAwaitingTextSinceAfterOrderByAwaitingTextSinceDesc(appUserId, since)
                .orElse(null);
    }

    /** Тема выбрана — черновик ждёт описание. Прежние ожидания сбрасываем: сообщение уйдёт только в новую. */
    public SupportTicket startDraft(Long appUserId, SupportTicket.Category category) {
        clearAwaiting(appUserId);
        return ticketRepository.save(new SupportTicket()
                .setAppUserId(appUserId)
                .setCategory(category)
                .setStatus(SupportTicket.Status.DRAFT)
                .setAwaitingTextSince(Instant.now()));
    }

    /** «✍️ Ответить» / «Дописать» — следующее сообщение уйдёт в эту заявку. null — заявка чужая или не открыта. */
    public SupportTicket startAppendOrNull(Long appUserId, Long ticketId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .filter(found -> found.getAppUserId().equals(appUserId))
                .filter(found -> found.getStatus() == SupportTicket.Status.OPEN)
                .orElse(null);
        if (ticket == null) return null;
        clearAwaiting(appUserId);
        return ticketRepository.save(ticket.setAwaitingTextSince(Instant.now()));
    }

    public void clearAwaiting(Long appUserId) {
        List<SupportTicket> awaiting = ticketRepository.findAllByAppUserIdAndAwaitingTextSinceIsNotNull(appUserId);
        awaiting.forEach(ticket -> ticket.setAwaitingTextSince(null));
        ticketRepository.saveAll(awaiting);
    }

    /**
     * Ответ (reply) пользователя на сообщение поддержки — заявка этого сообщения. null — ответ не на поддержку
     * или заявка чужая.
     */
    public SupportTicket findByReplyOrNull(Long appUserId, Integer replyToMessageId) {
        return messageRepository.findFirstByUserMessageIdAndDirection(replyToMessageId, SupportMessage.Direction.SUPPORT)
                .flatMap(message -> ticketRepository.findById(message.getTicketId()))
                .filter(ticket -> ticket.getAppUserId().equals(appUserId))
                .orElse(null);
    }

    public List<SupportTicket> getOpenTickets(Long appUserId) {
        return ticketRepository.findAllByAppUserIdAndStatusOrderByCreatedAtDesc(appUserId, SupportTicket.Status.OPEN);
    }

    /** Оценка закрытой заявки, один раз. null — заявка чужая, не закрыта или уже оценена. */
    public SupportTicket rateOrNull(Long appUserId, Long ticketId, boolean helped) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .filter(found -> found.getAppUserId().equals(appUserId))
                .filter(found -> found.getStatus() == SupportTicket.Status.CLOSED)
                .filter(found -> found.getRating() == null)
                .orElse(null);
        if (ticket == null) return null;
        return ticketRepository.save(ticket.setRating((short) (helped ? 1 : -1)));
    }
}
