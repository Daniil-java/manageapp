package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service;

import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.AiMessageLog;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.repositories.AiMessageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiMessageLogService {
    private final AiMessageLogRepository aiMessageLogRepository;

    public void saveLog(String request, String response) {
        aiMessageLogRepository.save(
                new AiMessageLog()
                        .setResponse(response)
                        .setRequest(request)
        );
    }
}
