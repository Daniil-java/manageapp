package com.kuklin.manageapp.bots.hhparserbot.processors;

import com.kuklin.manageapp.bots.hhparserbot.entities.HhUserInfo;
import com.kuklin.manageapp.bots.hhparserbot.entities.Vacancy;
import com.kuklin.manageapp.bots.hhparserbot.entities.WorkFilter;
import com.kuklin.manageapp.bots.hhparserbot.models.VacancyStatus;
import com.kuklin.manageapp.bots.hhparserbot.services.HhUserInfoService;
import com.kuklin.manageapp.bots.hhparserbot.services.HhVacancyService;
import com.kuklin.manageapp.bots.hhparserbot.services.HhWorkFilterService;
import com.kuklin.manageapp.bots.hhparserbot.telegram.HhTelegramBot;
import com.kuklin.manageapp.bots.hhparserbot.telegram.handlers.HhCallbackHandler;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.ThreadUtil;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class HhNotificationScheduleProcessor implements ScheduleProcessor {
    private final HhVacancyService hhVacancyService;
    private final HhTelegramBot hhTelegramBot;
    private final HhWorkFilterService hhWorkFilterService;
    private final HhUserInfoService hhUserInfoService;

    private static final int MAX_ATTEMPT_COUNT = 3;

    @Override
    public void process() {

        try {
            //Получение неотправлленых пользователю вакансий
            List<Vacancy> vacancyList = hhVacancyService.findByNotificationAttemptCountLessThan(MAX_ATTEMPT_COUNT);
            log.info("vacancy list size: " + vacancyList.size());
            for (Vacancy vacancy : vacancyList) {
                log.info(vacancy.getName());
                WorkFilter workFilter = hhWorkFilterService.getWorkFilterByIdOrNull(vacancy.getWorkFilterId());
                HhUserInfo hhUserInfo = hhUserInfoService.getHhUserInfoByIdOrNull(workFilter.getHhUserInfoId());
                long chatId = hhUserInfo.getTelegramId();
                //Проверка состояния отправленного сообщения
                if (hhTelegramBot.sendReturnedMessage(
                        chatId,
                        Vacancy.ToFormattedString(vacancy),
                        getInlineMessageButtonLetterGenerate(vacancy.getId()),
                        null) != null
                ) {
                    vacancy.setStatus(VacancyStatus.NOTIFICATED);
                } else {
                    vacancy.setNotificationAttemptCount(vacancy.getNotificationAttemptCount() + 1);
                    if (vacancy.getNotificationAttemptCount() > MAX_ATTEMPT_COUNT) {
                        vacancy.setStatus(VacancyStatus.NOTIFICATION_ERROR);
                    }
                }
                //Сохранение статуса отправки вакансии
                hhVacancyService.save(vacancy);
                ThreadUtil.sleep(1000);
            }
        } catch (Throwable t) {
            log.error("[NOTIFY] exception inside process()", t);
            throw t;
        }
    }

    private InlineKeyboardMarkup getInlineMessageButtonLetterGenerate(long vacancyId) {
        String callbackCommand = Command.HH_DECISION.getCommandText();
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton generateButton =
                new InlineKeyboardButton("Принять");
        InlineKeyboardButton rejectButton =
                new InlineKeyboardButton("Отклонить");
        generateButton.setCallbackData(callbackCommand + TelegramBot.DEFAULT_DELIMETER + VacancyStatus.APPLIED.name() + vacancyId);
        rejectButton.setCallbackData(callbackCommand + TelegramBot.DEFAULT_DELIMETER + VacancyStatus.REJECTED.name() + vacancyId);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();
        rowList.add(Arrays.asList(generateButton, rejectButton));

        inlineKeyboardMarkup.setKeyboard(rowList);
        return inlineKeyboardMarkup;
    }

    @Override
    public String getSchedulerName() {
        return this.getClass().getName();
    }
}
