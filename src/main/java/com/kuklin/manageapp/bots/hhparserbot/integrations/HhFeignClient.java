package com.kuklin.manageapp.bots.hhparserbot.integrations;

import com.kuklin.manageapp.bots.hhparserbot.models.HhEmployerDto;
import com.kuklin.manageapp.bots.hhparserbot.models.HhResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        value = "hh-feign-client",
        url = "${integrations.hh-api.url}"
)
public interface HhFeignClient {

    @GetMapping("/vacancies/{vacancyId}")
    HhResponseDto getVacancyById(@PathVariable Long vacancyId);

    @GetMapping("/employers/{employerId}")
    HhEmployerDto getEmployerById(@PathVariable Long employerId);
}
