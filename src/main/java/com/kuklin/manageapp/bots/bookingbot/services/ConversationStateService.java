package com.kuklin.manageapp.bots.bookingbot.services;

import com.kuklin.manageapp.bots.bookingbot.entities.ConversationState;
import com.kuklin.manageapp.bots.bookingbot.repositories.ConversationStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationStateService {
    private final ConversationStateRepository repository;

    public ConversationState getConversationStateByTelegramUserIdOrNull(Long telegramId) {
        return repository.findByTelegramId(telegramId).orElse(null);
    }

    public ConversationState setConversationState(Long telegramUserId, ConversationState.Step step) {
        return repository.save(new ConversationState()
                .setTelegramId(telegramUserId)
                .setStep(step)
        );
    }
}
