package com.kuklin.manageapp.bots.aiassistantcalendar.services.scheduler;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserAuthNotification;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserAuthNotificationService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class UserAuthNotificationSchedulerProcessor implements ScheduleProcessor {
    private final UserAuthNotificationService userAuthNotificationService;
    private final AssistantTelegramBot assistantTelegramBot;
    @Override
    public void process() {
        List<UserAuthNotification> list = userAuthNotificationService
                .getPendingUserAuthNotificationAndExpired();

        for (UserAuthNotification notification: list) {
            Message returnedMessage = assistantTelegramBot.sendReturnedMessage(
                    notification.getTelegramId(), notification.getText(),
                    getAuthButton(), null
            );

            if (returnedMessage != null) {
                notification.setStatus(UserAuthNotification.Status.NOTIFICATED);
                userAuthNotificationService.save(notification);
            } else {
                log.error("User auth notification error!");
            }
        }
    }

    public InlineKeyboardMarkup getAuthButton() {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();
        builder.row(TelegramKeyboard.button("Авторизоваться", Command.ASSISTANT_AUTH.getCommandText()));

        return builder.build();
    }


    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
