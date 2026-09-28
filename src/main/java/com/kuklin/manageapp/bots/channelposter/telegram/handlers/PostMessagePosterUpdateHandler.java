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
import com.kuklin.manageapp.common.services.TelegramService;
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
import java.util.UUID;

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
    private final TelegramService telegramService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasCallbackQuery()) {
            processCallback(update);
        }
    }

    private void processCallback(Update update) {
        String data = update.getCallbackQuery().getData();
        // Сразу получаем chatId безопасным методом
        Long chatId = extractChatId(update);

        try {
            String[] callbackData = data.split(TelegramBot.DEFAULT_DELIMETER);
            String cmd = callbackData[1];
            Long postId = Long.parseLong(callbackData[2]);

            PostQueue postQueue = postQueueService.getPostQueueById(postId);

            // Проверяем статус для ВСЕХ кнопок.
            // Если он не IMAGE_GENERATED, значит кто-то уже нажал кнопку на этом этапе.
            if (postQueue.getStatus() != PostQueue.PostQueueStatus.IMAGE_GENERATED) {
                channelPosterTelegramBot.sendReturnedMessage(
                        chatId,
                        "⚠️ Этот пост уже обработан (опубликован, запланирован или удален) другим админом."
                );
                return;
            }

            switch (cmd) {
                case APPROVE_CMD -> {
                    Integer msgId = sendPostContent(botKeyComponent.getChannelId(), postId, null);
                    // Если пост успешно отправлен в канал, меняем статус на SENT и удаляем картинку с диска
                    if (msgId != null) {
                        postQueueService.markAsSentAndDeleteFile(postId, msgId);
                        channelPosterTelegramBot.sendReturnedMessage(chatId, "✅ Пост моментально опубликован в канале.");
                    }
                }
                case REJECT_CMD -> {
                    postQueueService.removePost(postId);
                    channelPosterTelegramBot.sendReturnedMessage(chatId, "🗑 Пост отменен и удален.");
                }
                case SCHEDULE_CMD -> processScheduleCmd(chatId, postId);
            }

        } catch (PostQueueNotFoundException e) {
            channelPosterTelegramBot.sendReturnedMessage(chatId, "⚠️ Пост уже был удален другим админом.");
        } catch (Exception e) {
            log.error("Error processing callback in PostMessagePosterUpdateHandler", e);
            channelPosterTelegramBot.sendReturnedMessage(chatId, "❌ Произошла ошибка при обработке команды.");
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
    public Integer sendPostContent(Long chatId, Long postId, InlineKeyboardMarkup keyboard) throws PostQueueNotFoundException {
        PostQueue postQueue = postQueueService.getPostQueueById(postId);
        PostImage postImage = postImageService.getByPostQueueIdOrNull(postId);

        Message message = null;
        if (postImage != null) {
            byte[] image = telegramService.downloadFileOrNull(
                    channelPosterTelegramBot,
                    postImage.getTgFileId()
            );
            channelPosterTelegramBot.sendPhotoMessage(
                    chatId,
                    image,
                    UUID.randomUUID().toString(),
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