package com.kuklin.manageapp.bots.hhparserbot.services;

import com.kuklin.manageapp.bots.hhparserbot.configurations.TelegramHhParserBotKeyComponents;
import com.kuklin.manageapp.bots.hhparserbot.entities.Vacancy;
import com.kuklin.manageapp.bots.hhparserbot.models.VacancyStatus;
import com.kuklin.manageapp.bots.hhparserbot.entities.WorkFilter;
import com.kuklin.manageapp.bots.hhparserbot.models.HhEmployerDto;
import com.kuklin.manageapp.bots.hhparserbot.models.HhResponseDto;
import com.kuklin.manageapp.bots.hhparserbot.models.HhSimpleResponseDto;
import com.kuklin.manageapp.bots.hhparserbot.repositories.VacancyRepository;
import com.kuklin.manageapp.common.services.OpenAiIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HhVacancyService {
    private final VacancyRepository vacancyRepository;
    private final HhApiService hhApiService;
    private final OpenAiIntegrationService openAiIntegrationService;
    private final TelegramHhParserBotKeyComponents components;
    private static final String AI_REQUEST =
            """
                    Я отправляю тебе описание вакансии. 
                    Сократи описание, передай основные моменты.
                    Составляй сообщение от лица компании: \n %s
                    """;

    private static final String COVER_LETTER =
            "Помоги мне составить сопроводительное письмо для заявки на работу. " +
                    "Заявка найдена на сайте по поиску вакансий. " +
                    "Используй следующую информацию:\n" +
                    "\n" +
                    "1. Описание вакансии: %s\n" +
                    "2. Описание компании : %s\n" +
                    "3. Информация о соискателе: %s\n" +
                    "\n" +
                    "Требования к письму:\n" +
                    "- Письмо должно быть написано как реклама соискателя, подчеркивая совпадения навыков с требованиями вакансии.\n" +
                    "- Укажите, что я провел исследование компании и понимаю ее цели и ценности.\n" +
                    "- Письмо должно быть кратким, четким и профессиональным.\n" +
                    "- Необязательно использовать всю информацию о пользователе. " +
                    "Попробуй сгладить информацию пользователя, если это необходимо";

    public List<Vacancy> getAllByVacancyStatus(VacancyStatus vacancyStatus) {
        return vacancyRepository.findAllByStatus(vacancyStatus);
    }
    public List<Vacancy> getAllUnprocessedVacancies() {
        return vacancyRepository.findAllByNameIsNull();
    }

    public List<Vacancy> getAllUngeneratedVacancies() {
        return vacancyRepository.findAllByGeneratedDescriptionIsNullAndDescriptionIsNotNull();
    }

    public List<Vacancy> findByNotificationAttemptCountLessThan(int count) {
        return vacancyRepository.findProcessedVacanciesWithAttemptsLessThan(count);
    }

    public Vacancy save(Vacancy vacancy) {
        return vacancyRepository.save(vacancy);
    }

    public void parseHhVacancies(List<HhSimpleResponseDto> hhSimpleResponseDtos, WorkFilter workFilter) {
        //Обработка полученного списка ДТО-вакансий
        for (HhSimpleResponseDto dto: hhSimpleResponseDtos) {
            //Проверка на наличие уже существующих дубликатов в БД
            if (!vacancyRepository.findByHhIdAndWorkFilterId(dto.getHhId(), workFilter.getId())
                    .isPresent()) {
                //Конвертирование ДТО в сущность вакансии и сохранение
                vacancyRepository.save(new Vacancy()
                        .setUrl(dto.getUrl())
                        .setHhId(dto.getHhId())
                        .setWorkFilterId(workFilter.getId())
                        .setStatus(VacancyStatus.CREATED)
                );
            }
        }
    }


    //Обработка незаполненых вакансий, посредством обращения к api
    public void fetchAndSaveEntity(Vacancy vacancy) {
        //Получение ДТО-вакансии обращением к api
        HhResponseDto responseDto = hhApiService.getHhVacancyDtoByHhId(vacancy.getHhId());
        HhEmployerDto hhEmployerDto = hhApiService.getHhEmployerDtoByHhId(responseDto.getEmployer().getId());
        //Конвертация keySkills в String
        StringBuilder builder = new StringBuilder();
        if (responseDto.getKeySkills() != null) {
            for (String skill: responseDto.getKeySkills()) {
                builder.append(skill).append("|");
            }
        }

        //Конвертация ДТО в сущность вакансии и сохранение
        vacancyRepository.save(vacancy
                .setName(responseDto.getName())
                .setExperience(responseDto.getExperience().getName())
                .setKeySkills(builder.toString())
                .setEmployment(responseDto.getEmployment().getName())
                .setDescription(responseDto.getDescription())
                .setEmployerDescription(hhEmployerDto.getDescription())
                .setStatus(VacancyStatus.PARSED)
        );
    }

    //Сохранение сгенерированного описания вакансии, посредством обращения к OpenAI API
    public void fetchGenerateDescriptionAndUpdateEntity(Vacancy vacancy) {
        //Получение сгенерированного краткого описания, на основе описания полного
        String generatedDescription = openAiIntegrationService
                .fetchResponse(components.getAiKey(), String.format(AI_REQUEST, vacancy.getDescription()));
        //Обновление и сохранение данных вакансии
        vacancyRepository.save(vacancy
                .setGeneratedDescription(generatedDescription)
                .setStatus(VacancyStatus.PROCESSED)
        );
    }

    public String fetchGenerateCoverLetter(Long vacancyId, String userInfo) {
        Optional<Vacancy> vacancyOptional = vacancyRepository.findByIdAndDescriptionNotNull(vacancyId);
        if (vacancyOptional.isPresent()) {
            Vacancy vacancy = vacancyOptional.get();
            //Получение сгенерированного сопроводительного письма, на основе полного описания
            String vacancyDescription = "Ключевые навыки: " + vacancy.getKeySkills() + "\n" + vacancy.getDescription();
            String request = String.format(COVER_LETTER, vacancyDescription, vacancy.getEmployerDescription(), userInfo);
            return openAiIntegrationService.fetchResponse(components.getAiKey(), request);
        }
        return null;
    }

    public void updateStatusById(Long vacancyId, VacancyStatus vacancyStatus) {
        vacancyRepository.updateStatusById(vacancyId, vacancyStatus);
    }

    public void vacancyRejectById(long vacancyId) {
        vacancyRepository.updateStatusById(vacancyId, VacancyStatus.REJECTED);
    }
}
