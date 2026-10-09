package com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers.timer;


import com.kuklin.manageapp.bots.pomidorotimer.entities.Timer;
import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import com.kuklin.manageapp.bots.pomidorotimer.models.BotState;
import com.kuklin.manageapp.bots.pomidorotimer.services.LocaleMessageService;
import com.kuklin.manageapp.bots.pomidorotimer.services.PomidoroUserService;
import com.kuklin.manageapp.bots.pomidorotimer.services.TimerService;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.PomidoroTelegramBot;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers.MessageHandler;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.BotApiMethodBuilder;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.converters.NumeralConverter;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.keyboards.InlineKeyboardBuilder;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@AllArgsConstructor
public class TimerSettingsHandler implements MessageHandler {
    private final PomidoroUserService userService;
    private final PomidoroTelegramBot telegramBot;
    private final TimerService timerService;
    private final TimerHandler timerHandler;
    private final LocaleMessageService localeMessageService;
    private static final String VALUE_WAITING_MESSAGE = "reply.general.value-waiting";
    private static final String CLOSE_MESSAGE = "reply.general.close";
    private static final String WORK_DURATION_MESSAGE = "reply.timer.workDuration";
    private static final String SHORT_BREAK_DURATION_MESSAGE = "reply.timer.shortBreakDuration";
    private static final String LONG_BREAK_DURATION_MESSAGE = "reply.timer.longBreakDuration";
    private static final String LONG_BREAK_INTERVAL_MESSAGE = "reply.timer.longBreakInterval";
    private static final String IS_AUTOSTART_WORK_MESSAGE = "reply.timer.isAutostartWork";
    private static final String IS_AUTOSTART_BREAK_MESSAGE = "reply.timer.isAutostartBreak";
    private static final String CHOICE_MESSAGE = "reply.general.choice";
    private static final String BACK_COMMAND = "command.timer.back";

    @Override
    public BotApiMethod handle(Message message, UserEntity userEntity) {
        Long chatId = message.getChatId();
        int messageId = message.getMessageId();
        String userAnswer = message.getText();
        BotState botState = userEntity.getBotState();

        List<Timer> timers = timerService.getAnyNotCompleteTimerByUserIdOrNull(userEntity.getId());
        if (timers == null || timers.isEmpty()) {
            timers = timerService.getOrCreateTimerByUserId(userEntity.getId());
        }
        Timer timer = timers.get(0);

        int targetMsgId = timer.getTelegramMessageId(); // <— редактируем только карточку таймера
        EditMessageText editMessageText = BotApiMethodBuilder.makeEditMessageText(
                chatId, targetMsgId, localeMessageService.getMessage(VALUE_WAITING_MESSAGE)
        );

        if (BotState.TIMER_SETTINGS.equals(botState)) {
            // БЛОК "Назад" убран — возвращение делает callback с BotState.TIMER_STATUS
            editMessageText.setText(getTimerSettingsInfo(timer));
            editMessageText.setReplyMarkup(getInlineMessageButtons());
            return editMessageText;
        }

        if (Arrays.asList(BotState.TIMER_SETTINGS_AUTOSTART_BREAK, BotState.TIMER_SETTINGS_AUTOSTART_WORK).contains(botState)) {
            if ("true".equals(userAnswer) || "false".equals(userAnswer)) {
                if (botState.equals(BotState.TIMER_SETTINGS_AUTOSTART_BREAK)) {
                    timer.setAutostartBreak(Boolean.parseBoolean(userAnswer));
                } else {
                    timer.setAutostartWork(Boolean.parseBoolean(userAnswer));
                }
            } else {
                editMessageText.setText(localeMessageService.getMessage(CHOICE_MESSAGE));
                editMessageText.setReplyMarkup(InlineKeyboardBuilder.getTrueOrFalseK());
                return editMessageText;
            }
        } else {
            int value = NumeralConverter.parsePositiveSafelyInt(userAnswer);
            if (value > -1) {
                switch (botState) {
                    case TIMER_SETTINGS_LBREAK -> timer.setLongBreakDuration(value);
                    case TIMER_SETTINGS_WORK -> timer.setWorkDuration(value);
                    case TIMER_SETTINGS_SBREAK -> timer.setShortBreakDuration(value);
                    case TIMER_SETTINGS_LBREAK_INTERVAL -> timer.setLongBreakInterval(value);
                }
            } else {
                return editMessageText; // подсказка уже проставлена
            }
        }

        // Чистим сообщение пользователя (если возможно) и возвращаемся в меню настроек
        if (!(botState.equals(BotState.TIMER_SETTINGS_AUTOSTART_WORK) || botState.equals(BotState.TIMER_SETTINGS_AUTOSTART_BREAK))) {
            telegramBot.sendMessage(new DeleteMessage(String.valueOf(chatId), messageId));
        }
        timerService.updateTimerOrNull(timer);
        userEntity.setBotState(BotState.TIMER_SETTINGS);
        userService.updateUserEntity(userEntity);

        editMessageText.setText(getTimerSettingsInfo(timer));
        editMessageText.setReplyMarkup(getInlineMessageButtons());
        return editMessageText;
    }

    private static String getTimerSettingsInfo(Timer timer) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\uD83D\uDCBC ").append("<strong>Work duration: </strong>")
                .append(timer.getWorkDuration()).append(" min").append("\n");
        stringBuilder.append("⏸ ").append("<strong>Short break: </strong>")
                .append(timer.getShortBreakDuration()).append(" min").append("\n");
        stringBuilder.append("☕ ").append("<strong>Long break: </strong>")
                .append(timer.getLongBreakDuration()).append(" min").append("\n");
        stringBuilder.append("\uD83D\uDD01 ").append("<strong>Long break interval: </strong>")
                .append(timer.getLongBreakInterval()).append("\n");

        if (timer.isAutostartWork()) {
            stringBuilder.append("✔ ").append("<strong>Autostart work timer: </strong>ON").append("\n");
        } else {
            stringBuilder.append("❌ ").append("<strong>Autostart work timer: </strong>OFF").append("\n");
        }

        if (timer.isAutostartBreak()) {
            stringBuilder.append("✔ ").append("<strong>Autostart break timer: </strong>ON").append("\n");
        } else {
            stringBuilder.append("❌ ").append("<strong>Autostart break timer: </strong>OFF").append("\n");
        }

        return stringBuilder.toString();
    }

    private InlineKeyboardMarkup getInlineMessageButtons() {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton workDuration = new InlineKeyboardButton(localeMessageService.getMessage(WORK_DURATION_MESSAGE));
        InlineKeyboardButton shortBreakDuration = new InlineKeyboardButton(localeMessageService.getMessage(SHORT_BREAK_DURATION_MESSAGE));
        InlineKeyboardButton longBreakDuration = new InlineKeyboardButton(localeMessageService.getMessage(LONG_BREAK_DURATION_MESSAGE));
        InlineKeyboardButton longBreakInterval = new InlineKeyboardButton(localeMessageService.getMessage(LONG_BREAK_INTERVAL_MESSAGE));
        InlineKeyboardButton isAutostartWork = new InlineKeyboardButton(localeMessageService.getMessage(IS_AUTOSTART_WORK_MESSAGE));
        InlineKeyboardButton isAutostartBreak = new InlineKeyboardButton(localeMessageService.getMessage(IS_AUTOSTART_BREAK_MESSAGE));
        InlineKeyboardButton closeButton = new InlineKeyboardButton(localeMessageService.getMessage(CLOSE_MESSAGE));

        workDuration.setCallbackData(BotState.TIMER_SETTINGS_WORK.getCommand());
        shortBreakDuration.setCallbackData(BotState.TIMER_SETTINGS_SBREAK.getCommand());
        longBreakDuration.setCallbackData(BotState.TIMER_SETTINGS_LBREAK.getCommand());
        longBreakInterval.setCallbackData(BotState.TIMER_SETTINGS_LBREAK_INTERVAL.getCommand());
        isAutostartWork.setCallbackData(BotState.TIMER_SETTINGS_AUTOSTART_WORK.getCommand());
        isAutostartBreak.setCallbackData(BotState.TIMER_SETTINGS_AUTOSTART_BREAK.getCommand());
        closeButton.setCallbackData(BotState.TIMER_STATUS.getCommand());

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();
        rowList.add(Arrays.asList(workDuration));
        rowList.add(Arrays.asList(shortBreakDuration));
        rowList.add(Arrays.asList(longBreakDuration));
        rowList.add(Arrays.asList(longBreakInterval));
        rowList.add(Arrays.asList(isAutostartWork));
        rowList.add(Arrays.asList(isAutostartBreak));
        rowList.add(Arrays.asList(closeButton));

        inlineKeyboardMarkup.setKeyboard(rowList);
        return inlineKeyboardMarkup;
    }

    @Override
    public List<BotState> getHandlerListName() {
        return Arrays.asList(
                BotState.TIMER_SETTINGS,
                BotState.TIMER_SETTINGS_WORK, BotState.TIMER_SETTINGS_LBREAK,
                BotState.TIMER_SETTINGS_LBREAK_INTERVAL, BotState.TIMER_SETTINGS_AUTOSTART_WORK,
                BotState.TIMER_SETTINGS_AUTOSTART_BREAK, BotState.TIMER_SETTINGS_SBREAK);
    }
}
