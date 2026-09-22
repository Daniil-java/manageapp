package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.entities.ScheduleSlot;
import com.kuklin.manageapp.bots.channelposter.repositories.ScheduleSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleSlotService {

    private final ScheduleSlotRepository scheduleSlotRepository;

    public List<ScheduleSlot> getSlotByTopicCategoryIdAndIsActiveTrue(Long categoryId) {
        return scheduleSlotRepository.findByTopicCategoryIdAndIsActiveTrue(categoryId);
    }
}
