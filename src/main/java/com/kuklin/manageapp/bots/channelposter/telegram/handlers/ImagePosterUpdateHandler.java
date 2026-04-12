package com.kuklin.manageapp.bots.channelposter.telegram.handlers;

import com.kuklin.manageapp.bots.channelposter.entities.PostImage;
import com.kuklin.manageapp.bots.channelposter.services.PostQueueService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@RequiredArgsConstructor
@Component
@Slf4j
public class ImagePosterUpdateHandler implements ChannelPosterUpdateHandler {

    private final ChannelPosterTelegramBot channelPosterTelegramBot;
    private final PostQueueService postQueueService;
    private final PostMessagePosterUpdateHandler postMessagePosterUpdateHandler;

    // команды для callback'ов
    public static final String APPROVE_CMD = "APPROVE";
    public static final String REJECT_CMD = "REJECT";
    public static final String APPROVE_IMG_CMD = "APPIMG";
    public static final String REJECT_IMG_CMD = "REJIMG";
    public static final String REPEAT_IMG_CMD = "REPIMG";

    @Override
    // этот хендлер работает только с callback'ами
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasCallbackQuery()) {
            processCallback(update);
        }
    }

    private void processCallback(Update update) {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        try {
            // формат data: COMMAND ACTION POST_ID
            String cmd = data.split(TelegramBot.DEFAULT_DELIMETER)[1];
            Long postId = Long.parseLong(data.split(TelegramBot.DEFAULT_DELIMETER)[2]);

            // генерация картинки или повторная генерация
            if (cmd.equals(APPROVE_CMD) || cmd.equals(REPEAT_IMG_CMD)) {
                PostImage postImage = postQueueService.generateImage(postId);
                channelPosterTelegramBot.sendPhotoMessage(
                        chatId,
                        postImage.getFilePath(),
                        null,
                        getImgKeyboard(postId)
                );
                // удаление поста (как до генерации картинки, так и после)
            } else if (cmd.equals(REJECT_CMD) || cmd.equals(REJECT_IMG_CMD)) {
                postQueueService.removePost(postId);
                channelPosterTelegramBot.sendReturnedMessage(
                        chatId,
                        "Удалено"
                );
                // подтверждение изображения и переход к следующему этапу (постинг сообщения)
            } else if (cmd.equals(APPROVE_IMG_CMD)) {
                //Поменять статус. Отправить утверждение постинга
                postQueueService.setStatusImageCreated(postId);

                channelPosterTelegramBot.sendReturnedMessage(
                        chatId,
                        "Подтверждено"
                );
                // триггер следующего шага пайплайна
                postMessagePosterUpdateHandler.sendApproveMessage(chatId, postId);
            }
        } catch (Exception e) {
            channelPosterTelegramBot.sendReturnedMessage(
                    chatId,
                    "Не получилось выполнить комманду!"
            );
        }
    }

    private InlineKeyboardMarkup getImgKeyboard(Long postId) {
        //аппрув, перегенерация, удалить
        TelegramKeyboard.TelegramKeyboardBuilder builder =
                new TelegramKeyboard.TelegramKeyboardBuilder();

        builder
                .row(
                        TelegramKeyboard.button("✅APPROVE", getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + APPROVE_IMG_CMD + TelegramBot.DEFAULT_DELIMETER + postId),
                        TelegramKeyboard.button("REJECT", getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + REJECT_CMD + TelegramBot.DEFAULT_DELIMETER + postId)
                ).row(
                        TelegramKeyboard.button("\uD83D\uDD01REPEAT", getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + REPEAT_IMG_CMD + TelegramBot.DEFAULT_DELIMETER + postId)
                );

        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.POSTER_IMAGE.getCommandText();
    }
}
