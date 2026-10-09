package com.kuklin.manageapp.bots.channelposter.telegram.handlers;

import com.kuklin.manageapp.bots.channelposter.components.PostPublisher;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.model.PostQueueNotFoundException;
import com.kuklin.manageapp.bots.channelposter.services.PostImageService;
import com.kuklin.manageapp.bots.channelposter.services.PostQueueService;
import com.kuklin.manageapp.bots.channelposter.services.PostReviewService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import static com.kuklin.manageapp.bots.channelposter.services.PostReviewService.*;

/**
 * Кнопки под превью поста: в очередь, опубликовать сейчас, короче, другая картинка, без картинки, удалить.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewPosterUpdateHandler implements ChannelPosterUpdateHandler {

    public static final ZoneId CHANNEL_ZONE = ZoneId.of("Europe/Moscow");
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    // EXPIRED — снят с конвейера по сроку, но админ всё ещё может его принять;
    // TEXT_GENERATED / IMAGE_GENERATED — посты, созданные до одношаговой проверки
    private static final Set<PostQueue.PostQueueStatus> REVIEWABLE = Set.of(
            PostQueue.PostQueueStatus.REVIEW,
            PostQueue.PostQueueStatus.EXPIRED,
            PostQueue.PostQueueStatus.TEXT_GENERATED,
            PostQueue.PostQueueStatus.IMAGE_GENERATED
    );

    private final ChannelPosterTelegramBot bot;
    private final ChannelPosterBotKeyComponent keys;
    private final PostQueueService postQueueService;
    private final PostImageService postImageService;
    private final PostReviewService reviewService;
    private final PostPublisher publisher;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;
        bot.answerCallback(update);

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String[] data = update.getCallbackQuery().getData().split(TelegramBot.DEFAULT_DELIMETER);
        if (data.length < 3) return;
        String cmd = data[1];

        try {
            PostQueue post = postQueueService.getPostQueueById(Long.parseLong(data[2]));
            if (!REVIEWABLE.contains(post.getStatus())) {
                bot.sendReturnedMessage(chatId, "⚠️ Пост #" + post.getId() + " уже обработан (" + describe(post) + ").");
                bot.sendEditMessageReplyMarkupNull(chatId, messageId);
                return;
            }

            switch (cmd) {
                case QUEUE_CMD -> {
                    PostQueue queued = postQueueService.assignNextAvailableSlot(post, CHANNEL_ZONE);
                    if (queued == null) {
                        bot.sendReturnedMessage(chatId, "Нет свободных слотов на " + PostQueueService.SLOT_SEARCH_DAYS
                                + " дней вперёд — пост остался на проверке.");
                        return;
                    }
                    bot.sendEditMessageReplyMarkupNull(chatId, messageId);
                    bot.sendReturnedMessage(chatId, "✅ Пост #" + post.getId() + " в очереди: "
                            + queued.getScheduledAt().atZone(CHANNEL_ZONE).format(TIME_FORMAT));
                }
                case NOW_CMD -> {
                    Integer msgId = publisher.publish(keys.getChannelId(), post);
                    if (msgId == null) {
                        bot.sendReturnedMessage(chatId, "❌ Не получилось опубликовать пост #" + post.getId());
                        return;
                    }
                    postQueueService.markAsSentAndDeleteFile(post.getId(), msgId);
                    bot.sendEditMessageReplyMarkupNull(chatId, messageId);
                    bot.sendReturnedMessage(chatId, "📣 Пост #" + post.getId() + " опубликован.");
                }
                case SHORT_CMD -> {
                    bot.sendReturnedMessage(chatId, "✂️ Сокращаю…");
                    PostQueue shorter = postQueueService.makePostQueueContentShorter(post.getId());
                    bot.sendEditMessageReplyMarkupNull(chatId, messageId);
                    reviewService.sendPreview(chatId, shorter, true);
                }
                case IMAGE_CMD -> {
                    bot.sendReturnedMessage(chatId, "🎨 Рисую новую картинку…");
                    boolean ok = reviewService.generateImage(post, chatId);
                    if (!ok) {
                        bot.sendReturnedMessage(chatId, "❌ Картинка не получилась, попробуй ещё раз.");
                        return;
                    }
                    bot.sendEditMessageReplyMarkupNull(chatId, messageId);
                    reviewService.sendPreview(chatId, post, false);
                }
                case NO_IMAGE_CMD -> {
                    postImageService.deletePostImageByPostQueueId(post.getId());
                    bot.sendEditMessageReplyMarkupNull(chatId, messageId);
                    reviewService.sendPreview(chatId, post, false);
                }
                case DELETE_CMD -> {
                    postQueueService.removePost(post.getId());
                    bot.sendEditMessageReplyMarkupNull(chatId, messageId);
                    bot.sendReturnedMessage(chatId, "🗑 Пост #" + post.getId() + " удалён.");
                }
                default -> log.warn("Unknown review command: {}", cmd);
            }
        } catch (PostQueueNotFoundException e) {
            bot.sendReturnedMessage(chatId, "⚠️ Пост уже удалён.");
            bot.sendEditMessageReplyMarkupNull(chatId, messageId);
        } catch (Exception e) {
            log.error("Review callback failed: {}", update.getCallbackQuery().getData(), e);
            bot.sendReturnedMessage(chatId, "❌ Ошибка при обработке команды.");
        }
    }

    private static String describe(PostQueue post) {
        return switch (post.getStatus()) {
            case QUEUED -> "в очереди на " + post.getScheduledAt().atZone(CHANNEL_ZONE).format(TIME_FORMAT);
            case SENT -> "опубликован";
            case FAILED -> "ошибка публикации";
            default -> post.getStatus().name();
        };
    }

    @Override
    public String getHandlerListName() {
        return Command.POSTER_REVIEW.getCommandText();
    }
}
