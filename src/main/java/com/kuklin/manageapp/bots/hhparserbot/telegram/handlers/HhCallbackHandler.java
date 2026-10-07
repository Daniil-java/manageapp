package com.kuklin.manageapp.bots.hhparserbot.telegram.handlers;

import com.kuklin.manageapp.bots.hhparserbot.entities.HhUserInfo;
import com.kuklin.manageapp.bots.hhparserbot.models.VacancyStatus;
import com.kuklin.manageapp.bots.hhparserbot.services.HhUserInfoService;
import com.kuklin.manageapp.bots.hhparserbot.services.HhVacancyService;
import com.kuklin.manageapp.bots.hhparserbot.telegram.HhTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class HhCallbackHandler implements HhUpdateHandler {
    private static final String APPLIED_COMMAND = VacancyStatus.APPLIED.name();
    private static final String REJECTED_COMMAND = VacancyStatus.REJECTED.name();

    @Autowired
    private HhVacancyService vacancyService;
    @Autowired
    private HhUserInfoService hhUserInfoService;
    @Autowired
    @Lazy
    private HhTelegramBot telegramBot;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackData = callbackQuery.getData();

        String decision = callbackData.split(TelegramBot.DEFAULT_DELIMETER)[1];
        if (decision.startsWith(APPLIED_COMMAND)) {
            HhUserInfo hhUserInfo = hhUserInfoService.getHhUserInfoByTelegramIdOrCreate(telegramUser.getTelegramId());
            long vacancyId = Long.parseLong(decision.substring(APPLIED_COMMAND.length()));
            String coverLetter = vacancyService.fetchGenerateCoverLetter(vacancyId, hhUserInfo.getInfo());
            //Отправка сообщения пользователю и обработка, в случае удачного отправления
            if (telegramBot.sendReturnedMessage(chatId, coverLetter,
                    null, messageId) != null) {
                vacancyService.updateStatusById(vacancyId, VacancyStatus.APPLIED);
            }
        } else if (decision.startsWith(REJECTED_COMMAND)) {
            long vacancyId = Long.parseLong(decision.substring(REJECTED_COMMAND.length()));
            vacancyService.vacancyRejectById(vacancyId);
        }
    }


    @Override
    public String getHandlerListName() {
        return Command.HH_DECISION.getCommandText();
    }
}
