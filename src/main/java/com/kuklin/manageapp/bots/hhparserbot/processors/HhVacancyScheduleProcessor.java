package com.kuklin.manageapp.bots.hhparserbot.processors;

import com.kuklin.manageapp.bots.hhparserbot.entities.Vacancy;
import com.kuklin.manageapp.bots.hhparserbot.models.VacancyStatus;
import com.kuklin.manageapp.bots.hhparserbot.services.HhVacancyService;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.library.tgutils.ThreadUtil;
import feign.FeignException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class HhVacancyScheduleProcessor implements ScheduleProcessor {
    private final HhVacancyService hhVacancyService;
    private final static int MAX_EXCEPTION = 3;

    @Override
    public void process() {
        //Загрузка необработанных вакансий
        List<Vacancy> vacancyList = hhVacancyService
                .getAllByVacancyStatus(VacancyStatus.CREATED);

        //Счетчик возникших исключений
        int countException = 0;
        for (Vacancy vacancy : vacancyList) {
            if (countException > MAX_EXCEPTION) {
                log.error("VacancyScheduleProcessor: terminated due to errors");
                break;
            }
            try {
                //Обработка вакансий
                hhVacancyService.fetchAndSaveEntity(vacancy);
                ThreadUtil.sleep(100);
            } catch (FeignException.NotFound e) {
                // Вакансия удалена / недоступна в HH — не крит
                log.warn("VacancyScheduleProcessor: vacancy {} not found in HH (404). Marking as DELETED", vacancy.getHhId());
                hhVacancyService.save(vacancy.setStatus(VacancyStatus.NOT_FOUND_ERROR));


            } catch (Exception e) {
                log.error("VacancyScheduleProcessor: HH API error!", e);
                countException++;
            }
        }
    }

    @Override
    public String getSchedulerName() {
        return this.getClass().getName();
    }


}
