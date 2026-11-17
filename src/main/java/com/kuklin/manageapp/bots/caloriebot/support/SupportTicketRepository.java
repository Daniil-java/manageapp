package com.kuklin.manageapp.bots.caloriebot.support;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    /** Заявка, в которую ждём сообщение пользователя (не старше since). */
    Optional<SupportTicket> findFirstByAppUserIdAndAwaitingTextSinceAfterOrderByAwaitingTextSinceDesc(
            Long appUserId, Instant since);

    /** Все заявки, где ещё стоит ожидание — сбросить перед новым. */
    List<SupportTicket> findAllByAppUserIdAndAwaitingTextSinceIsNotNull(Long appUserId);

    Optional<SupportTicket> findByTopicId(Integer topicId);

    List<SupportTicket> findAllByAppUserIdAndStatusOrderByCreatedAtDesc(Long appUserId, SupportTicket.Status status);

    /** Прошлые заявки пользователя (кроме черновиков), новые — первыми. */
    List<SupportTicket> findTop5ByAppUserIdAndStatusNotAndIdNotOrderByCreatedAtDesc(
            Long appUserId, SupportTicket.Status status, Long excludedId);

    long countByAppUserIdAndStatusNot(Long appUserId, SupportTicket.Status status);

    /** Открытые заявки, где последнее сообщение — от пользователя, без ответа с before и без напоминания после него. */
    @Query("""
            select t from SupportTicket t
            where t.status = com.kuklin.manageapp.bots.caloriebot.support.SupportTicket.Status.OPEN
              and t.lastUserMessageAt < :before
              and (t.lastSupportMessageAt is null or t.lastSupportMessageAt < t.lastUserMessageAt)
              and (t.remindedAt is null or t.remindedAt < t.lastUserMessageAt)
            """)
    List<SupportTicket> findUnanswered(@Param("before") Instant before);
}
