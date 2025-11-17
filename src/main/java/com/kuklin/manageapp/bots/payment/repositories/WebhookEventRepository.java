package com.kuklin.manageapp.bots.payment.repositories;

import com.kuklin.manageapp.bots.payment.entities.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    @Query("""
        select count(e)>0 from WebhookEvent e
        where e.provider = ?1 and e.event = ?2 and e.objectId = ?3 and e.processed = true
    """)
    boolean isAlreadyProcessed(WebhookEvent.WebhookProvider provider,
                               WebhookEvent.WebhookEventType event,
                               String objectId);
}