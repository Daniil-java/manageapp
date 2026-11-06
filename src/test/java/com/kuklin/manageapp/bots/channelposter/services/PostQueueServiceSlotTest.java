package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.ScheduleSlot;
import com.kuklin.manageapp.bots.channelposter.repositories.PostQueueRepository;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostQueueServiceSlotTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final PostQueueRepository repository = mock(PostQueueRepository.class);
    private final ScheduleSlotService scheduleSlotService = mock(ScheduleSlotService.class);
    private final PostQueueService service = new PostQueueService(
            repository, null, null, null, scheduleSlotService, null, null);

    @Test
    void weekdaySlotIsUsedOnlyOnItsDay() {
        // завтрашний день недели: слот гарантированно впереди, но не сегодня
        DayOfWeek tomorrow = LocalDate.now(ZONE).plusDays(1).getDayOfWeek();
        when(scheduleSlotService.getSlotByTopicCategoryIdAndIsActiveTrue(2L)).thenReturn(new ArrayList<>(List.of(
                new ScheduleSlot().setDayOfWeek(tomorrow.getValue()).setPostTime(LocalTime.of(0, 1)))));
        when(repository.findPlannedAndSentFor(any(), any(), any())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PostQueue queued = service.assignNextAvailableSlot(new PostQueue().setCategoryId(2L), ZONE);

        ZonedDateTime at = queued.getScheduledAt().atZone(ZONE);
        assertThat(at.toLocalDate()).isEqualTo(LocalDate.now(ZONE).plusDays(1));
        assertThat(at.toLocalTime()).isEqualTo(LocalTime.of(0, 1));
        assertThat(queued.getStatus()).isEqualTo(PostQueue.PostQueueStatus.QUEUED);
    }
}
