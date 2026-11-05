package com.kuklin.manageapp.bots.caloriebot.support;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    /** Сообщение поддержки в чате пользователя — по нему ответ (reply) пользователя находит заявку. */
    Optional<SupportMessage> findFirstByUserMessageIdAndDirection(Integer userMessageId, SupportMessage.Direction direction);

    /** Первое сообщение пользователя в заявке — суть обращения для списка прошлых заявок. */
    Optional<SupportMessage> findFirstByTicketIdAndDirectionOrderByCreatedAtAsc(Long ticketId, SupportMessage.Direction direction);
}
