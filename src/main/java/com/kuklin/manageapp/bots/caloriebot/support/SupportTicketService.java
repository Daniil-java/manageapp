package com.kuklin.manageapp.bots.caloriebot.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    public SupportTicket findByIdOrNull(Long ticketId) {
        return ticketRepository.findById(ticketId).orElse(null);
    }

    /** Заявка темы группы поддержки; null — тема не заявки. */
    public SupportTicket findByTopicIdOrNull(Integer topicId) {
        return ticketRepository.findByTopicId(topicId).orElse(null);
    }

    public SupportTicket saveTicket(SupportTicket ticket) {
        return ticketRepository.save(ticket);
    }

    public SupportMessage saveMessage(SupportMessage message) {
        return messageRepository.save(message);
    }

    /** @see SupportTicketRepository#findUnanswered */
    public List<SupportTicket> findUnanswered(Instant before) {
        return ticketRepository.findUnanswered(before);
    }

    /** До 5 последних прошлых заявок пользователя (без черновиков и без этой) — для карточки. */
    public List<SupportTicket> findPreviousTickets(SupportTicket ticket) {
        return ticketRepository.findTop5ByAppUserIdAndStatusNotAndIdNotOrderByCreatedAtDesc(
                ticket.getAppUserId(), SupportTicket.Status.DRAFT, ticket.getId());
    }

    /** Сколько заявок пользователь отправил (без черновиков). */
    public long countSubmittedTickets(Long appUserId) {
        return ticketRepository.countByAppUserIdAndStatusNot(appUserId, SupportTicket.Status.DRAFT);
    }

    /** Первое сообщение пользователя в заявке — превью в карточке. */
    public Optional<SupportMessage> findFirstUserMessage(Long ticketId) {
        return messageRepository.findFirstByTicketIdAndDirectionOrderByCreatedAtAsc(ticketId,
                SupportMessage.Direction.USER);
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
