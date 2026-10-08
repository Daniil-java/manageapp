package com.kuklin.manageapp.bots.hhparserbot.services;

import com.kuklin.manageapp.bots.hhparserbot.entities.WorkFilter;
import com.kuklin.manageapp.bots.hhparserbot.models.HhEmployerDto;
import com.kuklin.manageapp.bots.hhparserbot.models.HhResponseDto;
import com.kuklin.manageapp.bots.hhparserbot.models.HhSimpleResponseDto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@AllArgsConstructor
@Slf4j
public class HhApiService {
    //С апреля 2026 api.hh.ru отвечает 403 на запросы без авторизации приложения,
    //поэтому данные вакансий и работодателей берутся со страниц сайта.
    //Используется hh.kz, а не hh.ru: hh.ru может не открываться без российского IP
    //(сервер стоит за границей), а на hh.kz те же вакансии, id и вёрстка
    private static final String HH_URL = "https://hh.kz";
    //hh.ru и региональные поддомены (spb.hh.ru и т.п.) — регион всё равно задаётся параметром area
    private static final Pattern HH_RU_HOST_PATTERN = Pattern.compile("^https?://([\\w-]+\\.)*hh\\.ru");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118 Safari/537.36";
    private static final Pattern EMPLOYER_ID_PATTERN = Pattern.compile("/employer/(\\d+)");

    //Загрузка страницы, с использованием Jsoup, и парсинг результатов в сущности
    public List<HhSimpleResponseDto> loadAndParseHhVacancies(WorkFilter workFilter) {
        //Пользователи сохраняют ссылки на поиск с hh.ru, а без российского IP он может не открыться —
        //поэтому переписываем домен на hh.kz (параметры поиска у сайтов совпадают)
        String url = HH_RU_HOST_PATTERN.matcher(workFilter.getUrl()).replaceFirst(HH_URL);
        List<HhSimpleResponseDto> hhSimpleResponseDtos = new ArrayList<>();
        try {
            //Получение страницы
            Document document = loadPage(url);
            //Выборка необходимых элементов(вакансий) страницы
            Elements elements = document.select("a[data-qa='serp-item__title']");
            //Ограничене на количество новых вакансий
            int limit = 75;
            for (Element element: elements) {
                if (limit-- <= 0) break;;
                //Получение ссылки на вакансию
                String vacancyUrl = element.attr("href");
                //Валидация полученной ссылки
                if (!vacancyUrl.contains("click") && vacancyUrl.indexOf("?") > -1) {
                    //Создание ДТО вакансии и добавление в возвращаемый список
                    hhSimpleResponseDtos.add(new HhSimpleResponseDto()
                            .setHhId(getVacancyIdFromUrl(vacancyUrl))
                            .setUrl(vacancyUrl)
                    );
                }
            }
        } catch (IOException e) {
            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                log.warn("HH request interrupted for url={}", url);
            } else {
                log.error("HhApiService: Jsoup connection error!", e);
            }
        }
        return hhSimpleResponseDtos;
    }


    //Получение id-вакансии из ссылка на вакансию
    private Long getVacancyIdFromUrl(String url) {
        return Long.valueOf(url.substring(
                        url.lastIndexOf("/") + 1, url.indexOf("?")));
    }

    //Получение ДТО-вакансии по id, посредством парсинга страницы вакансии
    public HhResponseDto getHhVacancyDtoByHhId(Long vacancyId) {
        Document document = loadPageOrThrow(HH_URL + "/vacancy/" + vacancyId, vacancyId);
        Element description = document.selectFirst("[data-qa=vacancy-description]");
        //У архивной вакансии нет описания — считаем её недоступной
        if (description == null) {
            throw new HhVacancyNotFoundException(vacancyId);
        }

        HhResponseDto dto = new HhResponseDto();
        dto.setId(String.valueOf(vacancyId));
        dto.setAlternateUrl(HH_URL + "/vacancy/" + vacancyId);
        dto.setName(textOrNull(document, "[data-qa=vacancy-title]"));
        dto.setDescription(description.html());

        HhResponseDto.Experience experience = new HhResponseDto.Experience();
        experience.setName(textOrNull(document, "[data-qa=vacancy-experience]"));
        dto.setExperience(experience);

        HhResponseDto.Employment employment = new HhResponseDto.Employment();
        employment.setName(textOrNull(document, "[data-qa=common-employment-text]"));
        dto.setEmployment(employment);

        dto.setKeySkillsItems(document.select("[data-qa=skills-element]").stream()
                .map(element -> {
                    HhResponseDto.KeySkillItem item = new HhResponseDto.KeySkillItem();
                    item.setName(element.text());
                    return item;
                })
                .toList());

        //Работодатель может быть скрыт (анонимная вакансия)
        Element companyLink = document.selectFirst("a[data-qa=vacancy-company-name]");
        if (companyLink != null) {
            Matcher matcher = EMPLOYER_ID_PATTERN.matcher(companyLink.attr("href"));
            if (matcher.find()) {
                dto.setEmployer(new HhEmployerDto().setId(Long.valueOf(matcher.group(1))));
            }
        }
        return dto;
    }

    //Получение ДТО-работодателя по id, посредством парсинга страницы работодателя
    public HhEmployerDto getHhEmployerDtoByHhId(Long employerId) {
        Document document = loadPageOrThrow(HH_URL + "/employer/" + employerId, employerId);
        return new HhEmployerDto()
                .setId(employerId)
                .setDescription(textOrNull(document, "[data-qa=employer-view-widget-description]"));
    }

    private Document loadPageOrThrow(String url, Long hhId) {
        try {
            return loadPage(url);
        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                throw new HhVacancyNotFoundException(hhId);
            }
            //Скрытые/заблокированные вакансии hh отдаёт с 403 стабильно, хотя остальные страницы открываются.
            //Если же 403 на всё (заблокировали наш IP) — не помечаем вакансию недоступной, а падаем как раньше
            if (e.getStatusCode() == 403 && isSiteAccessible()) {
                throw new HhVacancyNotFoundException(hhId);
            }
            throw new UncheckedIOException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Document loadPage(String url) throws IOException {
        return Jsoup.connect(url).userAgent(USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .timeout(15000)
                .get();
    }

    private boolean isSiteAccessible() {
        try {
            loadPage(HH_URL);
            return true;
        } catch (IOException e) {
            log.warn("HH main page is not accessible: {}", e.getMessage());
            return false;
        }
    }

    private String textOrNull(Document document, String cssQuery) {
        Element element = document.selectFirst(cssQuery);
        return element == null ? null : element.text();
    }

    public static class HhVacancyNotFoundException extends RuntimeException {
        public HhVacancyNotFoundException(Long hhId) {
            super("HH page not found or archived: " + hhId);
        }
    }
}
