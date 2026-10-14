package com.kuklin.manageapp.bots.hhparserbot.integrations;

import com.kuklin.manageapp.bots.hhparserbot.models.HhEmployerDto;
import com.kuklin.manageapp.bots.hhparserbot.models.HhResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @deprecated С апреля 2026 api.hh.ru отвечает 403 Forbidden на запросы без авторизованного
 * приложения (доступ выдаётся только работодателям и рекрутинговым сервисам после модерации).
 * Данные вакансий и работодателей теперь парсятся со страниц сайта — см. {@link
 * com.kuklin.manageapp.bots.hhparserbot.services.HhApiService}.
 */
@Deprecated
@FeignClient(
        value = "hh-feign-client",
        url = "${integrations.hh-api.url}"
)
public interface HhFeignClient {

    @GetMapping("/vacancies/{vacancyId}")
    HhResponseDto getVacancyById(@PathVariable(name = "vacancyId") Long vacancyId);

    @GetMapping("/employers/{employerId}")
    HhEmployerDto getEmployerById(@PathVariable(name = "employerId") Long employerId);
}
