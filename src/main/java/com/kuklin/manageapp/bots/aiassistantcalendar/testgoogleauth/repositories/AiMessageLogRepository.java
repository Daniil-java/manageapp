package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.repositories;

import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.AiMessageLog;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.AssistantGoogleOAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiMessageLogRepository extends JpaRepository<AiMessageLog, Long> {
}
