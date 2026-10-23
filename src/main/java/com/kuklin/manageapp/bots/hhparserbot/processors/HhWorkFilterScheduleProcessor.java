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
    //Сколько страниц поиска (по 20 вакансий, свежие первыми) читать за прогон на один фильтр
    private static final int MAX_PAGES = 3;

    private final HhWorkFilterService hhWorkFilterService;
    private final HhVacancyService hhVacancyService;

    @Override
    public void process() {
        //Формирование общего листа пользовательских ссылок
        List<WorkFilter> workFilterList = hhWorkFilterService.getAll();
        int totalNew = 0;
        //Загрузка, парсинг и сохранение в БД id вакансий
        for (WorkFilter workFilter: workFilterList) {
            //Поиск отсортирован по дате: читаем страницы, пока на них есть новые вакансии
            for (int page = 0; page < MAX_PAGES; page++) {
                //Получение ДТО вакансий
                List<HhSimpleResponseDto> hhSimpleResponseDtos =
                        hhWorkFilterService.loadHhVacancies(workFilter, page);
                //Парсинг полученных вакансий
                int newCount = hhVacancyService.parseHhVacancies(hhSimpleResponseDtos, workFilter);
                totalNew += newCount;
                ThreadUtil.sleep(1000);
                //Пустая страница (конец выдачи или ошибка загрузки) или только уже известные — дальше старые
                if (hhSimpleResponseDtos.isEmpty() || newCount == 0) break;
            }
        }
        log.info("HH filters: {} filters, {} new vacancies", workFilterList.size(), totalNew);
    }

    @Override
    public String getSchedulerName() {
        return this.getClass().getName();
    }

}
