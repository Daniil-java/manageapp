package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.components.ArticleContentGenerator;
import com.kuklin.manageapp.bots.channelposter.components.PostPublisher;
import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import com.kuklin.manageapp.bots.channelposter.entities.PostImage;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Проверка поста админом в одно касание: картинка генерируется сразу, админ получает готовое превью
 * с кнопками «в очередь / сейчас / короче / другая картинка / без картинки / удалить».
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostReviewService {

    public static final String QUEUE_CMD = "QUEUE";
    public static final String NOW_CMD = "NOW";
    public static final String SHORT_CMD = "SHORT";
    public static final String IMAGE_CMD = "IMG";
    public static final String NO_IMAGE_CMD = "NOIMG";
    public static final String DELETE_CMD = "DEL";

    private final ChannelPosterTelegramBot bot;
    private final ChannelPosterBotKeyComponent keys;
    private final ArticleContentGenerator contentGenerator;
    private final PostImageService postImageService;
    private final PostQueueService postQueueService;
    private final PostPublisher publisher;
    private final ChannelPosterProperties properties;

    /**
     * Генерирует картинку, переводит пост в REVIEW и рассылает превью.
     */
    public void prepareAndSend(PostQueue post, List<Long> chatIds) {
        // первый получатель уже видит картинку — ему превью без повтора фото
        Long imageShownTo = generateImage(post, chatIds.get(0)) ? chatIds.get(0) : null;
        PostQueue review = postQueueService.markReview(post);
        chatIds.forEach(chatId -> sendPreview(chatId, review, !chatId.equals(imageShownTo)));
    }

    public void prepareAndSendToAdmins(PostQueue post) {
        List<Long> admins = keys.getAdminIds();
        if (admins.isEmpty()) {
            log.warn("Post #{}: no CHANNELPOSTER_ADMIN_IDS, preview not sent", post.getId());
            postQueueService.markReview(post);
            return;
        }
        prepareAndSend(post, admins);
    }

    /**
     * Новая картинка. Фото сразу уходит в chatId, а его file_id в Telegram сохраняем — с ним пост и публикуется.
     *
     * @return true, если картинка готова
     */
    public boolean generateImage(PostQueue post, Long chatId) {
        try {
            byte[] image = contentGenerator.generateImage(post);
            if (image == null) {
                return false;
            }
            Message message = bot.sendPhotoMessage(chatId, image, UUID.randomUUID().toString(), null, null);
            if (message == null || message.getPhoto() == null || message.getPhoto().isEmpty()) {
                return false;
            }
            String fileId = message.getPhoto().get(message.getPhoto().size() - 1).getFileId();
            postImageService.saveNewImage(PostImage.ImageSource.AI_GENERATED, PostImage.ImageStatus.READY, fileId, post.getId());
            return true;
        } catch (Exception e) {
            log.error("Post #{}: image generation failed", post.getId(), e);
            return false;
        }
    }

    public void sendPreview(Long chatId, PostQueue post, boolean withImage) {
        publisher.send(chatId, post, footer(post), keyboard(post.getId()), withImage);
    }

    private String footer(PostQueue post) {
        StringBuilder sb = new StringBuilder("\n\n———\n<i>Пост #").append(post.getId());
        if (post.getAiScore() != null) {
            sb.append(" · оценка AI ").append(post.getAiScore()).append("/10");
        }
        if (postImageService.getByPostQueueIdOrNull(post.getId()) == null) {
            sb.append(" · без картинки");
        }
        if (isAutopilotCandidate(post)) {
            sb.append("\n🤖 Если не ответишь за ").append(humanize(properties.getAutopilotAfter()))
                    .append(", автопилот сам поставит пост в очередь");
        } else if (post.getReviewSentAt() != null) {
            sb.append("\n⏳ Не проверишь за ").append(humanize(properties.getReviewTtl()))
                    .append(" — пост снимется с конвейера (кнопки останутся рабочими)");
        }
        return sb.append("</i>").toString();
    }

    /**
     * Пост, который автопилот поставит в очередь сам: оценка AI от channelposter.autopilot-min-score
     * и автопилот включён (channelposter.autopilot-after больше нуля). Ручные посты без оценки — нет.
     */
    public boolean isAutopilotCandidate(PostQueue post) {
        return isAutopilotEnabled()
                && post.getAiScore() != null
                && post.getAiScore() >= properties.getAutopilotMinScore();
    }

    public boolean isAutopilotEnabled() {
        Duration after = properties.getAutopilotAfter();
        return after != null && !after.isZero() && !after.isNegative();
    }

    // 12h → «12 ч», 3d → «3 дн.», 90m → «90 мин»
    public static String humanize(Duration d) {
        if (d.toMinutes() % 60 != 0) return d.toMinutes() + " мин";
        if (d.toHours() % 24 != 0) return d.toHours() + " ч";
        return d.toDays() + " дн.";
    }

    public static InlineKeyboardMarkup keyboard(Long postId) {
        return new TelegramKeyboard.TelegramKeyboardBuilder()
                .row(
                        TelegramKeyboard.button("✅ В очередь", data(QUEUE_CMD, postId)),
                        TelegramKeyboard.button("⚡ Сейчас", data(NOW_CMD, postId))
                )
                .row(
                        TelegramKeyboard.button("✂️ Короче", data(SHORT_CMD, postId)),
                        TelegramKeyboard.button("🔁 Картинка", data(IMAGE_CMD, postId)),
                        TelegramKeyboard.button("🚫 Без картинки", data(NO_IMAGE_CMD, postId))
                )
                .row(TelegramKeyboard.button("❌ Удалить", data(DELETE_CMD, postId)))
                .build();
    }

    private static String data(String cmd, Long postId) {
        return Command.POSTER_REVIEW.getCommandText() + TelegramBot.DEFAULT_DELIMETER + cmd
                + TelegramBot.DEFAULT_DELIMETER + postId;
    }
}
