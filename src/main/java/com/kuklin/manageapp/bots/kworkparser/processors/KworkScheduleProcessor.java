package com.kuklin.manageapp.bots.kworkparser.processors;

import com.kuklin.manageapp.bots.kworkparser.entities.Url;
import com.kuklin.manageapp.bots.kworkparser.models.KworkDto;
import com.kuklin.manageapp.bots.kworkparser.parser.KworkParser;
import com.kuklin.manageapp.bots.kworkparser.services.KworkService;
import com.kuklin.manageapp.bots.kworkparser.services.UrlService;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.library.tgutils.ThreadUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class KworkScheduleProcessor implements ScheduleProcessor {
    private final KworkParser kworkParser;
    private final UrlService urlService;
    private final KworkService kworkService;

    /*
    Процесс сбора новых данных
     */
    @Override
    public void process() {
        //Получение всех сыллок
        List<Url> urls = urlService.getAllUrls();

        for (Url url: urls) {
            //Получение распаршенных данных
            List<KworkDto> kworkDtos = kworkParser.getKworksOrNull(url.getUrl());
            for (KworkDto dto: kworkDtos) {
                //Сохранение данных, с учетом существования дубликатов
                kworkService.saveOrNull(dto, url.getId());
            }
            ThreadUtil.sleep(100);
        }

    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
