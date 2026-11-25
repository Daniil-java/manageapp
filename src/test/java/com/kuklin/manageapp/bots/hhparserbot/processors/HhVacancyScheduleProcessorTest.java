package com.kuklin.manageapp.bots.hhparserbot.processors;

import com.kuklin.manageapp.bots.hhparserbot.entities.Vacancy;
import com.kuklin.manageapp.bots.hhparserbot.models.VacancyStatus;
import com.kuklin.manageapp.bots.hhparserbot.services.HhApiService;
import com.kuklin.manageapp.bots.hhparserbot.services.HhVacancyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HhVacancyScheduleProcessorTest {

    private final HhVacancyService service = mock(HhVacancyService.class);
    private final HhVacancyScheduleProcessor processor = new HhVacancyScheduleProcessor(service);

    @Test
    @SuppressWarnings("unchecked")
    void sameHhIdFromDifferentFiltersIsFetchedOnceNewestFirst() {
        when(service.getAllByVacancyStatus(VacancyStatus.CREATED))
                .thenReturn(List.of(vacancy(1, 100), vacancy(2, 300), vacancy(3, 100), vacancy(4, 200)));

        processor.process();

        ArgumentCaptor<List<Vacancy>> groups = ArgumentCaptor.forClass(List.class);
        verify(service, times(3)).fetchAndSaveEntities(groups.capture());
        assertThat(groups.getAllValues()).extracting(g -> g.get(0).getHhId()).containsExactly(300L, 200L, 100L);
        assertThat(groups.getAllValues().get(2)).extracting(Vacancy::getId).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void notFoundMarksWholeGroup() {
        List<Vacancy> group = List.of(vacancy(1, 100), vacancy(2, 100));
        when(service.getAllByVacancyStatus(VacancyStatus.CREATED)).thenReturn(group);
        doThrow(new HhApiService.HhVacancyNotFoundException(100L)).when(service).fetchAndSaveEntities(any());

        processor.process();

        assertThat(group).extracting(Vacancy::getStatus).containsOnly(VacancyStatus.NOT_FOUND_ERROR);
        verify(service).saveAll(any());
    }

    @Test
    void stopsAfterTooManyErrors() {
        when(service.getAllByVacancyStatus(VacancyStatus.CREATED))
                .thenReturn(LongStream.rangeClosed(1, 10).mapToObj(i -> vacancy(i, i)).toList());
        doThrow(new UncheckedIOException(new SocketTimeoutException("Read timed out")))
                .when(service).fetchAndSaveEntities(any());

        processor.process();

        verify(service, times(4)).fetchAndSaveEntities(any());
    }

    @Test
    void processesAtMostHundredPerRun() {
        // «не найдено» не ждёт паузу между запросами — тест быстрый
        List<Vacancy> many = new ArrayList<>();
        for (long i = 1; i <= 150; i++) many.add(vacancy(i, i));
        when(service.getAllByVacancyStatus(VacancyStatus.CREATED)).thenReturn(many);
        doThrow(new HhApiService.HhVacancyNotFoundException(0L)).when(service).fetchAndSaveEntities(any());

        processor.process();

        verify(service, times(100)).fetchAndSaveEntities(any());
    }

    private static Vacancy vacancy(long id, long hhId) {
        Vacancy vacancy = new Vacancy();
        vacancy.setId(id);
        vacancy.setHhId(hhId);
        vacancy.setStatus(VacancyStatus.CREATED);
        return vacancy;
    }
}
