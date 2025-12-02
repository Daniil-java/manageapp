package com.kuklin.manageapp.bots.hhparserbot.processors;


import com.kuklin.manageapp.bots.hhparserbot.entities.WorkFilter;
import com.kuklin.manageapp.bots.hhparserbot.models.HhSimpleResponseDto;
import com.kuklin.manageapp.bots.hhparserbot.services.HhVacancyService;
import com.kuklin.manageapp.bots.hhparserbot.services.HhWorkFilterService;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.library.tgutils.ThreadUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class HhWorkFilterScheduleProcessor implements ScheduleProcessor {

    private final HhWorkFilterService hhWorkFilterService;
    private final HhVacancyService hhVacancyService;

    @Override
    public void process() {
        //Формирование общего листа пользовательских ссылок
        List<WorkFilter> workFilterList = hhWorkFilterService.getAll();
        //Загрузка, парсинг и сохранение в БД id вакансий
        for (WorkFilter workFilter: workFilterList) {
            //Получение ДТО вакансий
            List<HhSimpleResponseDto> hhSimpleResponseDtos =
                    hhWorkFilterService.loadHhVacancies(workFilter);
            //Парсинг полученных вакансий
            hhVacancyService.parseHhVacancies(hhSimpleResponseDtos, workFilter);
            ThreadUtil.sleep(1000);
        }
    }

    @Override
    public String getSchedulerName() {
        return this.getClass().getName();
    }

}
