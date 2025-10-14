package com.kuklin.manageapp.bots.bookingbot.services;

import com.kuklin.manageapp.bots.bookingbot.entities.FormAnswer;
import com.kuklin.manageapp.bots.bookingbot.repositories.FormAnswerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormAnswerService {
    private final FormAnswerRepository repository;

    /**
     * Создать или обновить анкету для брони
     */
    public FormAnswer save(FormAnswer formAnswer) {
        return repository.save(formAnswer);
    }

    /**
     * Найти анкету по id брони
     */
    public FormAnswer findByBookingIdOrNull(Long bookingId) {
        return repository.findByBookingId(bookingId).orElse(null);
    }

    /**
     * Найти анкету по id самой анкеты
     */
    public FormAnswer findByIdOrNull(Long id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * Удалить анкету по id
     */
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    /**
     * Удалить анкету по bookingId
     */
    @Transactional
    public void deleteByBookingId(Long bookingId) {
        repository.findByBookingId(bookingId)
                .ifPresent(repository::delete);
    }

}
