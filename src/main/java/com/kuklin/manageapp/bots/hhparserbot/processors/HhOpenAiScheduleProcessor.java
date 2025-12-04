package com.kuklin.manageapp.bots.hhparserbot.processors;

import com.kuklin.manageapp.bots.hhparserbot.entities.Vacancy;
import com.kuklin.manageapp.bots.hhparserbot.models.VacancyStatus;
import com.kuklin.manageapp.bots.hhparserbot.services.HhVacancyService;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.kuklin.manageapp.bots.hhparserbot.models.VacancyStatus.OPEN_AI_ERROR;


@Component
@AllArgsConstructor
@Slf4j
public class HhOpenAiScheduleProcessor implements ScheduleProcessor {
    private final HhVacancyService hhVacancyService;
    private final static int MAX_EXCEPTION = 3;

    @Override
    public void process() {
        //Получение вакансий без сгенерированного описания
        // 1) Берём все PARSED-вакансии
        List<Vacancy> vacancyList = hhVacancyService.getAllByVacancyStatus(VacancyStatus.PARSED);
        log.info("[AI] PARSED vacancies count={}", vacancyList.size());

        // 2) Группируем по hhId — чтобы на одну вакансию HH был один запрос
        Map<Long, List<Vacancy>> groupedByHhId = vacancyList.stream()
                .collect(Collectors.groupingBy(Vacancy::getHhId));

        int countException = 0;
        int limit = 99;

        for (Map.Entry<Long, List<Vacancy>> entry : groupedByHhId.entrySet()) {
            if (countException > MAX_EXCEPTION) {
                log.error("[AI] Exceeded max exception count ({}). Stop processing", MAX_EXCEPTION);
                break;
            }

            long hhId = entry.getKey();
            List<Vacancy> group = entry.getValue();

            try {
                // 3) Сначала смотрим, нет ли уже ОБРАБОТАННОЙ вакансии по этому hhId (например, из прошлого запуска)
                Vacancy alreadyProcessed = hhVacancyService.findProcessedByHhId(hhId);
                if (alreadyProcessed != null && alreadyProcessed.getGeneratedDescription() != null) {
                    log.info("[AI] hhId={} already processed, reuse generated description", hhId);

                    for (Vacancy vacancy : group) {
                        vacancy
                                .setGeneratedDescription(alreadyProcessed.getGeneratedDescription())
                                .setStatus(alreadyProcessed.getStatus()); // PROCESSED
                        hhVacancyService.save(vacancy);
                    }
                    continue; // к следующему hhId — в ИИ не идём
                }

                // 4) Иначе вызываем ИИ ТОЛЬКО ДЛЯ ОДНОЙ вакансии из группы
                Vacancy mainVacancy = group.get(0);
                log.info("[AI] call OpenAI for hhId={}, vacancyId={}", hhId, mainVacancy.getId());

                if (limit-- <= 0) break;
                hhVacancyService.fetchGenerateDescriptionAndUpdateEntity(mainVacancy);
                String generatedDescription = mainVacancy.getGeneratedDescription();

                if (generatedDescription == null) {
                    // Если по какой-то причине описание не сгенерировалось, не копируем его на дубли
                    log.error("[AI] generatedDescription is null after OpenAI call for hhId={}, vacancyId={}",
                            hhId, mainVacancy.getId());
                    continue;
                }

                // 5) Копируем результат во все остальные дубликаты того же hhId
                for (Vacancy vacancy : group) {
                    if (vacancy.getId().equals(mainVacancy.getId())) {
                        continue; // основную уже сохранили внутри сервиса
                    }

                    vacancy
                            .setGeneratedDescription(generatedDescription)
                            .setStatus(mainVacancy.getStatus()); // PROCESSED
                    hhVacancyService.save(vacancy);
                }

            } catch (Exception e) {
                for (Vacancy vacancy: group) {
                    vacancy.setStatus(OPEN_AI_ERROR);
                }
                hhVacancyService.saveAll(group);
                countException++;
                log.error("[AI] Error while generating description for hhId={}", entry.getKey(), e);
            }
        }
    }

    @Override
    public String getSchedulerName() {
        return this.getClass().getName();
    }
}
