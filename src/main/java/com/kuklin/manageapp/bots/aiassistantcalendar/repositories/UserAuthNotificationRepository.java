package com.kuklin.manageapp.bots.aiassistantcalendar.repositories;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserAuthNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserAuthNotificationRepository extends JpaRepository<UserAuthNotification, Long> {

    List<UserAuthNotification> findByStatusAndExecuteAtLessThanEqualOrderByExecuteAtAsc(
            UserAuthNotification.Status status,
            LocalDateTime executeAt
    );
}
