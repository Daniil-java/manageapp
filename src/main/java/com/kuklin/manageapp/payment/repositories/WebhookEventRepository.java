package com.kuklin.manageapp.payment.repositories;

import com.kuklin.manageapp.payment.entities.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    @Query("""
        select count(e)>0 from WebhookEvent e
        where e.provider = ?1 and e.event = ?2 and e.objectId = ?3 and e.processed = true
    """)
    boolean isAlreadyProcessed(WebhookEvent.WebhookProvider provider,
                               WebhookEvent.WebhookEventType event,
                               String objectId);

    // Сохранённые, но не обработанные события (ни processed, ни error) за окно receivedAt
    @Query("""
        select e.id from WebhookEvent e
        where e.provider = ?1 and e.processed = false and e.error is null
          and e.receivedAt > ?2 and e.receivedAt < ?3
        order by e.id
    """)
    List<Long> findUnhandledIds(WebhookEvent.WebhookProvider provider,
                                Instant receivedAfter,
                                Instant receivedBefore);
}