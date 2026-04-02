package com.kuklin.manageapp.bots.channelposter.telegram.handlers;

import com.kuklin.manageapp.bots.channelposter.entities.PostImage;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.model.PostQueueNotFoundException;
import com.kuklin.manageapp.bots.channelposter.services.PostImageService;
import com.kuklin.manageapp.bots.channelposter.services.PostQueueService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
@Component
@Slf4j
public class PostMessagePosterUpdateHandler implements ChannelPosterUpdateHandler {

    // команды для callback
    private static final String APPROVE_CMD = "APPROVE";
    private static final String REJECT_CMD = "REJECT";
    private static final String SCHEDULE_CMD = "SCHEDULE";
    // дефолтная таймзона канала
    public static final ZoneId defChannelZoneId = ZoneId.of("Europe/Moscow");
    // id канала, куда публикуем

    private final ChannelPosterBotKeyComponent botKeyComponent;
    private final ChannelPosterTelegramBot channelPosterTelegramBot;
    private final PostQueueService postQueueService;
    private final PostImageService postImageService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasCallbackQuery()) {
            processCallback(update);
        }
    }

    private void processCallback(Update update) {
        String data = update.getCallbackQuery().getData();
        try {
            // Убираем дублирование split()
            String[] callbackData = data.split(TelegramBot.DEFAULT_DELIMETER);
            String cmd = callbackData[1];
            Long postId = Long.parseLong(callbackData[2]);

            // Используем константы первыми (защита от NullPointerException)
            switch (cmd) {
                case APPROVE_CMD -> sendPostContent(botKeyComponent.getChannelId(), postId, null);
                case REJECT_CMD -> postQueueService.removePost(postId);
                case SCHEDULE_CMD -> processScheduleCmd(update.getCallbackQuery().getMessage().getChatId(), postId);
            }

        } catch (Exception e) {
            channelPosterTelegramBot.sendReturnedMessage(
                    extractChatId(update),
                    "не получилось"
            );
        }
    }

    /**
     * Назначает ближайший свободный слот публикации и сообщает пользователю время
     */
    private void processScheduleCmd(Long chatId, Long postId) throws PostQueueNotFoundException {
        PostQueue postQueue = postQueueService.assignNextAvailableSlot(postId, defChannelZoneId);

        String msg = "Ошибка! Не удалось запланировать пост!";
        if (postQueue != null) {
            Instant time = postQueue.getScheduledAt();
            ZonedDateTime moscowTime = time.atZone(defChannelZoneId);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
            msg = "Пост будет опубликован: " + moscowTime.format(formatter);
        }

        channelPosterTelegramBot.sendReturnedMessage(chatId, msg);

    }

    /**
     * Отправляет сообщение с кнопками подтверждения перед финальным постингом
     */
    public Integer sendApproveMessage(Long chatId, Long postId) {
        try {
            return sendPostContent(chatId, postId, getPostApproveKeyboard(postId));
        } catch (PostQueueNotFoundException e) {
            log.warn("Пост не найден: {}", postId, e);
            channelPosterTelegramBot.sendReturnedMessage(
                    chatId,
                    "Не получилось сформировать сообщение для поста"
            );
        }
        return null;
    }

    // --- Вспомогательные методы (устранение дублирования) ---

    /**
     * Общая логика получения сущностей и отправки сообщения с фото или без
     */
    private Integer sendPostContent(Long chatId, Long postId, InlineKeyboardMarkup keyboard) throws PostQueueNotFoundException {
        PostQueue postQueue = postQueueService.getPostQueueById(postId);
        PostImage postImage = postImageService.getByPostQueueIdOrNull(postId);

        Message message = null;
        if (postImage != null) {
            channelPosterTelegramBot.sendPhotoMessage(
                    chatId,
                    postImage.getFilePath(),
                    null,
                    null
            );
            //Ограничение на кол-во символов при отправке фото - 1024
            message = channelPosterTelegramBot.sendReturnedMessage(
                    chatId,
                    postQueue.getTextContent(),
                    keyboard,
                    null
            );
        } else {
            if (keyboard != null) {
                channelPosterTelegramBot.sendReturnedMessage(
                        chatId,
                        postQueue.getTextContent(),
                        keyboard,
                        null
                );
            } else {
                message = channelPosterTelegramBot.sendReturnedMessage(
                        chatId,
                        postQueue.getTextContent()
                );
            }
        }

        if (message != null) {
            postQueueService.save(postQueue.setTgMessageId(message.getMessageId()));
            return message.getMessageId();
        }
        return null;
    }

    private Long extractChatId(Update update) {
        return update.hasMessage() ?
                update.getMessage().getChatId() :
                update.getCallbackQuery().getMessage().getChatId();
    }

    private InlineKeyboardMarkup getPostApproveKeyboard(Long postId) {
        // аппрув, перегенерация, удалить
        TelegramKeyboard.TelegramKeyboardBuilder builder =
                new TelegramKeyboard.TelegramKeyboardBuilder();

        builder.row(
                TelegramKeyboard.button("✅POST", getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + APPROVE_CMD + TelegramBot.DEFAULT_DELIMETER + postId),
                TelegramKeyboard.button("REJECT", getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + REJECT_CMD + TelegramBot.DEFAULT_DELIMETER + postId)
        ).row(
                TelegramKeyboard.button("SCHEDULE", getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + SCHEDULE_CMD + TelegramBot.DEFAULT_DELIMETER + postId)
        );

        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.POSTER_POST.getCommandText();
    }
}