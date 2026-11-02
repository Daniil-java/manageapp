package com.kuklin.manageapp.bots.hhparserbot.processors;

import com.kuklin.manageapp.bots.hhparserbot.entities.Vacancy;
import com.kuklin.manageapp.bots.hhparserbot.models.VacancyStatus;
import com.kuklin.manageapp.bots.hhparserbot.services.HhApiService;
import com.kuklin.manageapp.bots.hhparserbot.services.HhVacancyService;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.library.tgutils.ThreadUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
@Slf4j
public class HhVacancyScheduleProcessor implements ScheduleProcessor {
    private final HhVacancyService hhVacancyService;
    private final static int MAX_EXCEPTION = 3;
    //Вакансий HH (разных hhId) за прогон: 2–3 запроса на вакансию + пауза — около 10 минут.
    //Без лимита бэклог в 600 вакансий занял планировщик на 51 минуту; остаток — в следующих прогонах
    private final static int MAX_PER_RUN = 100;

    @Override
    public void process() {
        long startedAt = System.currentTimeMillis();
        //Загрузка необработанных вакансий
        List<Vacancy> vacancyList = hhVacancyService
                .getAllByVacancyStatus(VacancyStatus.CREATED);

        //Одна вакансия HH из разных фильтров — одна загрузка страницы. Свежие первыми (больший hhId — новее)
        Map<Long, List<Vacancy>> groupedByHhId = vacancyList.stream()
                .sorted(Comparator.comparing(Vacancy::getHhId).reversed())
                .collect(Collectors.groupingBy(Vacancy::getHhId, LinkedHashMap::new, Collectors.toList()));

        //Счетчики для итога прогона
        int parsed = 0, notFound = 0, countException = 0;
        boolean terminated = false;
        for (Map.Entry<Long, List<Vacancy>> entry : groupedByHhId.entrySet()) {
            if (countException > MAX_EXCEPTION) {
                terminated = true;
                break;
            }
            if (parsed + notFound + countException >= MAX_PER_RUN) break;

            Long hhId = entry.getKey();
            List<Vacancy> group = entry.getValue();
            try {
                //Обработка вакансий
                hhVacancyService.fetchAndSaveEntities(group);
                parsed++;
                ThreadUtil.sleep(500);
            } catch (HhApiService.HhVacancyNotFoundException e) {
                // Вакансия удалена / в архиве — не крит
                log.warn("VacancyScheduleProcessor: vacancy {} not found or archived in HH. Marking as NOT_FOUND_ERROR", hhId);
                group.forEach(vacancy -> vacancy.setStatus(VacancyStatus.NOT_FOUND_ERROR));
                hhVacancyService.saveAll(group);
                notFound++;
            } catch (UncheckedIOException e) {
                //Ответ hh с HTTP-ошибкой или сетевая ошибка — причина важнее стектрейса
                log.error("VacancyScheduleProcessor: HH page error for vacancy {}: {}", hhId, describe(e.getCause()));
                countException++;
            } catch (Exception e) {
                log.error("VacancyScheduleProcessor: error for vacancy {}", hhId, e);
                countException++;
            }
        }

        int left = groupedByHhId.size() - parsed - notFound;
        long seconds = (System.currentTimeMillis() - startedAt) / 1000;
        if (terminated) {
            log.error("VacancyScheduleProcessor: terminated due to errors. Parsed {}, not found {}, errors {}, left {}, {} s",
                    parsed, notFound, countException, left, seconds);
        } else {
            log.info("VacancyScheduleProcessor: parsed {}, not found {}, errors {}, left {}, {} s",
                    parsed, notFound, countException, left, seconds);
        }
    }

    //«HTTP 429 https://hh.kz/vacancy/123» или «SocketTimeoutException: Read timed out»
    private static String describe(Throwable e) {
        if (e instanceof HttpStatusException httpError) {
            return "HTTP " + httpError.getStatusCode() + " " + httpError.getUrl();
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    @Override
    public String getSchedulerName() {
        return this.getClass().getName();
    }


}
