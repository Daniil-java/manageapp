package com.kuklin.manageapp.bots.hhparserbot.processors;

import com.kuklin.manageapp.bots.hhparserbot.entities.Vacancy;
import com.kuklin.manageapp.bots.hhparserbot.models.VacancyStatus;
import com.kuklin.manageapp.bots.hhparserbot.services.HhVacancyService;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@AllArgsConstructor
@Slf4j
public class HhOpenAiScheduleProcessor implements ScheduleProcessor {
    private final HhVacancyService hhVacancyService;
    private final static int MAX_EXCEPTION = 3;

    @Override
    public void process() {
        //Получение вакансий без сгенерированного описания
        List<Vacancy> vacancyList = hhVacancyService.getAllByVacancyStatus(VacancyStatus.PARSED);

        //Счетчик ошибок
        int countException = 0;
        for (Vacancy vacancy: vacancyList) {
            if (countException > MAX_EXCEPTION) {
                log.error("OpenAiScheduleProcessor: terminated due to errors");
                break;
            }
            try {
                //Обработка вакансии для генерации описания
                hhVacancyService.fetchGenerateDescriptionAndUpdateEntity(vacancy);
            } catch (Exception e) {
                log.error("OpenAiScheduleProcessor: generation error!", e);
                countException++;
            }
        }
    }

    @Override
    public String getSchedulerName() {
        return this.getClass().getName();
    }
}
