package com.kuklin.manageapp.bots.kworkparser.processors;

import com.kuklin.manageapp.bots.kworkparser.entities.Kwork;
import com.kuklin.manageapp.bots.kworkparser.entities.Url;
import com.kuklin.manageapp.bots.kworkparser.services.UrlKworkService;
import com.kuklin.manageapp.bots.kworkparser.services.UrlService;
import com.kuklin.manageapp.bots.kworkparser.services.UserKworkNotificationService;
import com.kuklin.manageapp.bots.kworkparser.services.UserUrlService;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.services.TelegramService;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduleProcessor implements ScheduleProcessor {
    private final TelegramUserService telegramUserService;
    private final UserUrlService userUrlService;
    private final UrlService urlService;
    private final UrlKworkService urlKworkService;
    private final UserKworkNotificationService userKworkNotificationService;
    @Override
    public void process() {
        //Получение всех ссылок
        List<Url> urlList = urlService.getAllUrls();

        for (Url url: urlList) {
            //Данные для рассылки
            List<Long> telegramUsersIds = userUrlService.getAllUsersIdByUrlId(url.getId());
            List<Kwork> kworks = urlKworkService.findNewKworksByUrlId(url.getId());
            userKworkNotificationService.notificate(telegramUsersIds, kworks);

        }

    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
