package com.kuklin.manageapp.bots.channelposter.repositories;

import com.kuklin.manageapp.bots.channelposter.entities.ScheduleSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, Long> {
    List<ScheduleSlot> findByTopicCategoryIdAndIsActiveTrue(Long categoryId);
}
