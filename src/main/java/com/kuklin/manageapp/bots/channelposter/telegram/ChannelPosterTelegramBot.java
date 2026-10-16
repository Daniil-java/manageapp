package com.kuklin.manageapp.bots.channelposter.telegram;

import com.kuklin.manageapp.bots.channelposter.services.ChannelPollService;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class ChannelPosterTelegramBot extends TelegramBot {

    private final List<Long> adminsIds;

    @Autowired
    private ChannelPosterTelegramFacade channelPosterTelegramFacade;

    @Autowired
    private ChannelPollService channelPollService;

    public ChannelPosterTelegramBot(ChannelPosterBotKeyComponent components) {
        super(components.getKey());
        adminsIds = components.getAdminIds();
    }

    @Override
    public void handleUpdateDirectly(Update update) {
        channelPosterTelegramFacade.handleUpdate(update);
    }

    @Override
    public void onUpdateReceived(Update update) {
        // новое состояние опроса, отправленного ботом (голоса в канале)
        if (update.hasPoll()) {
            try {
                channelPollService.updateVotes(update.getPoll());
            } catch (Exception e) {
                log.error("Can't save poll votes {}", update.getPoll().getId(), e);
            }
            return;
        }
        if (!(update.hasMessage() || update.hasCallbackQuery())) {
            return;
        }
        Long userId = update.hasMessage() ?
                update.getMessage().getFrom().getId() :
                update.getCallbackQuery().getFrom().getId();
        if (!adminsIds.contains(userId)) return;
        channelPosterTelegramFacade.handleUpdate(update);
    }

    /**
     * Фото с HTML-подписью (до 1024 символов).
     *
     * @return отправленное сообщение или null, если Telegram не принял (например, не разобрал HTML)
     */
    public Message sendHtmlPhoto(long chatId, byte[] photo, String caption, InlineKeyboardMarkup keyboard) {
        SendPhoto sendPhoto = SendPhoto.builder()
                .chatId(String.valueOf(chatId))
                .photo(new InputFile(new ByteArrayInputStream(photo), UUID.randomUUID() + ".jpg"))
                .caption(caption)
                .parseMode(ParseMode.HTML)
                .replyMarkup(keyboard)
                .build();
        try {
            return execute(sendPhoto);
        } catch (TelegramApiException e) {
            log.warn("Can't send photo with caption to {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    /**
     * Анонимный опрос (в каналах другие не разрешены). correctOptionId не null — квиз с пояснением.
     *
     * @return отправленное сообщение (в нём id опроса) или null, если Telegram не принял
     */
    public Message sendPoll(long chatId, String question, List<String> options,
                            Integer correctOptionId, String explanation) {
        SendPoll sendPoll = SendPoll.builder()
                .chatId(String.valueOf(chatId))
                .question(question)
                .options(options)
                .isAnonymous(true)
                .build();
        if (correctOptionId != null) {
            sendPoll.setType("quiz");
            sendPoll.setCorrectOptionId(correctOptionId);
            sendPoll.setExplanation(explanation);
        }
        try {
            return execute(sendPoll);
        } catch (TelegramApiException e) {
            log.warn("Can't send poll to {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BotIdentifier.CHANNEL_POSTER;
    }

    @Override
    public String getBotUsername() {
        return BotIdentifier.CHANNEL_POSTER.getBotUsername();
    }
}
