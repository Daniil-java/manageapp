package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.components.ArticleContentGenerator;
import com.kuklin.manageapp.bots.channelposter.components.PostPublisher;
import com.kuklin.manageapp.bots.channelposter.components.source.PixabayClient;
import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import com.kuklin.manageapp.bots.channelposter.entities.PostImage;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
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

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Проверка поста админом в одно касание: картинка (фото с Pixabay, без ключа — генерация) подбирается сразу,
 * админ получает готовое превью с кнопками «в очередь / сейчас / другое фото / сгенерировать / короче /
 * без картинки / удалить».
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostReviewService {

    public static final String QUEUE_CMD = "QUEUE";
    public static final String NOW_CMD = "NOW";
    public static final String SHORT_CMD = "SHORT";
    // сгенерировать картинку через AI (у старых превью кнопка «🔁 Картинка» — то же самое)
    public static final String IMAGE_CMD = "IMG";
    // следующее фото с Pixabay
    public static final String PHOTO_CMD = "PHOTO";
    // сколько фото выдачи подряд пробуем, если Telegram не берёт ссылку
    static final int STOCK_TRIES = 3;
    public static final String NO_IMAGE_CMD = "NOIMG";
    public static final String DELETE_CMD = "DEL";

    private final ChannelPosterTelegramBot bot;
    private final ChannelPosterBotKeyComponent keys;
    private final ArticleContentGenerator contentGenerator;
    private final PostImageService postImageService;
    private final PostQueueService postQueueService;
    private final PostPublisher publisher;
    private final ChannelPosterProperties properties;
    private final TopicCategoryService topicCategoryService;
    private final ChannelPollService pollService;
    private final PixabayClient stock;

    /**
     * Подбирает картинку, переводит пост в REVIEW и рассылает превью.
     */
    public void prepareAndSend(PostQueue post, List<Long> chatIds) {
        // опрос выходит без картинки
        if (!isPoll(post)) {
            attachDefaultImage(post, chatIds.get(0));
        }
        PostQueue review = postQueueService.markReview(post);
        chatIds.forEach(chatId -> sendPreview(chatId, review, true));
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
     * Картинка по умолчанию — фото с Pixabay по ключевым словам от AI, генерация не тратится.
     * Без ключа Pixabay или ключевых слов (посты до et-109) — генерация, как раньше.
     * Фото не нашлось — пост без картинки: админ нажмёт «🎨 Сгенерировать», автопилот выпустит так.
     */
    private void attachDefaultImage(PostQueue post, Long chatId) {
        if (!canSearchStock(post)) {
            generateImage(post, chatId);
            return;
        }
        if (!attachStockPhoto(post, chatId)) {
            log.info("Post #{}: no Pixabay photo for «{}» / «{}», left without image",
                    post.getId(), post.getImageQuery(), post.getImageQueryBroad());
        }
    }

    /**
     * «🔁 Другое фото»: следующее после текущего фото той же выдачи Pixabay.
     *
     * @return true, если фото сменилось; false — фото кончились, нет ключа или ключевых слов
     */
    public boolean attachNextStockPhoto(PostQueue post, Long chatId) {
        if (!canSearchStock(post)) return false;
        return attachStockPhoto(post, chatId);
    }

    private boolean canSearchStock(PostQueue post) {
        return keys.hasPixabayKey() && post.getImageQuery() != null && !post.getImageQuery().isBlank();
    }

    /**
     * Первое фото выдачи после текущего фото поста (нет текущего — с начала), которое принял Telegram.
     * Пробуем несколько подряд — ссылка может не открыться.
     */
    boolean attachStockPhoto(PostQueue post, Long chatId) {
        List<PixabayClient.StockPhoto> photos;
        try {
            photos = findStockPhotos(post);
        } catch (IOException e) {
            log.warn("Post #{}: Pixabay search failed for «{}»: {}", post.getId(), post.getImageQuery(), e.getMessage());
            return false;
        }
        int from = 0;
        for (int i = 0; i < photos.size(); i++) {
            if (Objects.equals(photos.get(i).id(), post.getStockPhotoId())) {
                from = i + 1;
                break;
            }
        }
        for (int i = from; i < photos.size() && i < from + STOCK_TRIES; i++) {
            PixabayClient.StockPhoto photo = photos.get(i);
            PostImage image = saveSentPhoto(post, bot.sendPhotoByUrl(chatId, photo.imageUrl()),
                    PostImage.ImageSource.STOCK, chatId);
            if (image == null) {
                log.warn("Post #{}: Telegram didn't take photo {}", post.getId(), photo.imageUrl());
                continue;
            }
            postImageService.save(image.setStockAuthor(photo.author()).setStockPageUrl(photo.pageUrl()));
            postQueueService.save(post.setStockPhotoId(photo.id()));
            return true;
        }
        return false;
    }

    /**
     * Выдача для поста: сначала фото по точному запросу, за ними — по запасному (без дублей).
     * Фото других постов (на проверке, в очереди, вышедших) убраны — в канале они не повторяются.
     */
    List<PixabayClient.StockPhoto> findStockPhotos(PostQueue post) throws IOException {
        Map<Long, PixabayClient.StockPhoto> photos = new LinkedHashMap<>();
        stock.searchByKeywords(post.getImageQuery()).forEach(photo -> photos.putIfAbsent(photo.id(), photo));
        String broad = post.getImageQueryBroad();
        if (broad != null && !broad.isBlank() && !broad.trim().equalsIgnoreCase(post.getImageQuery().trim())) {
            stock.searchByKeywords(broad).forEach(photo -> photos.putIfAbsent(photo.id(), photo));
        }
        Set<Long> taken = postQueueService.findStockPhotoIdsOfOtherPosts(photos.keySet(), post.getId());
        return photos.values().stream().filter(photo -> !taken.contains(photo.id())).toList();
    }

    /**
     * «🚫 Без картинки»: картинку убираем, фото со стока освобождается для других постов.
     */
    public void removeImage(PostQueue post) {
        postImageService.deletePostImageByPostQueueId(post.getId());
        releaseStockPhoto(post);
    }

    private void releaseStockPhoto(PostQueue post) {
        if (post.getStockPhotoId() != null) {
            postQueueService.save(post.setStockPhotoId(null));
        }
    }

    /**
     * Новая картинка через AI. Фото уходит в chatId только ради file_id в Telegram (с ним пост и публикуется)
     * и сразу удаляется: превью следом показывает пост так, как он выйдет в канале, — с этой картинкой.
     *
     * @return true, если картинка готова
     */
    public boolean generateImage(PostQueue post, Long chatId) {
        try {
            // генератор сразу удаляет прежнюю картинку — её фото со стока больше не у этого поста
            byte[] image = contentGenerator.generateImage(post);
            releaseStockPhoto(post);
            if (image == null) {
                return false;
            }
            Message message = bot.sendPhotoMessage(chatId, image, UUID.randomUUID().toString(), null, null);
            return saveSentPhoto(post, message, PostImage.ImageSource.AI_GENERATED, chatId) != null;
        } catch (Exception e) {
            log.error("Post #{}: image generation failed", post.getId(), e);
            return false;
        }
    }

    /**
     * Картинка поста по публичной ссылке (фото со стока): так же через первого админа — ради file_id.
     *
     * @return true, если картинка сохранена
     */
    public boolean attachImageFromUrl(PostQueue post, String url) {
        List<Long> admins = keys.getAdminIds();
        if (admins.isEmpty()) return false;
        Long chatId = admins.get(0);
        return saveSentPhoto(post, bot.sendPhotoByUrl(chatId, url), PostImage.ImageSource.STOCK, chatId) != null;
    }

    /**
     * file_id фото, только что отправленного в chatId, — к посту вместо прежней картинки; само сообщение удаляем.
     *
     * @return сохранённая картинка или null, если фото не ушло
     */
    private PostImage saveSentPhoto(PostQueue post, Message message, PostImage.ImageSource source, Long chatId) {
        if (message == null || message.getPhoto() == null || message.getPhoto().isEmpty()) {
            return null;
        }
        String fileId = message.getPhoto().get(message.getPhoto().size() - 1).getFileId();
        postImageService.deletePostImageByPostQueueId(post.getId());
        PostImage image = postImageService.saveNewImage(source, PostImage.ImageStatus.READY, fileId, post.getId());
        bot.sendDeleteMessage(chatId, message.getMessageId());
        return image;
    }

    public void sendPreview(Long chatId, PostQueue post, boolean withImage) {
        InlineKeyboardMarkup buttons = isPoll(post) ? pollKeyboard(post.getId()) : keyboard(post.getId());
        publisher.send(chatId, post, footer(post), buttons, withImage);
    }

    private boolean isPoll(PostQueue post) {
        return pollService.findByPostQueueId(post.getId()).isPresent();
    }

    private String footer(PostQueue post) {
        StringBuilder sb = new StringBuilder("\n\n———\n<i>Пост #").append(post.getId());
        topicCategoryService.findById(post.getCategoryId())
                .ifPresent(category -> sb.append(" · ").append(category.getName()));
        if (post.getAiScore() != null) {
            sb.append(" · оценка AI ").append(post.getAiScore()).append("/10");
        }
        PostImage image = postImageService.getByPostQueueIdOrNull(post.getId());
        if (image == null && !isPoll(post)) {
            sb.append(" · без картинки");
        } else if (image != null && image.getStockAuthor() != null && post.getImageQuery() != null) {
            // по каким словам искали — видно, почему фото такое
            sb.append(" · фото Pixabay: «").append(PostPublisher.escapeHtml(post.getImageQuery())).append("»");
            if (post.getImageQueryBroad() != null) {
                sb.append(" / «").append(PostPublisher.escapeHtml(post.getImageQueryBroad())).append("»");
            }
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
     * Пост, который автопилот поставит в очередь сам, если автопилот включён (channelposter.autopilot-after
     * больше нуля): оценка AI от channelposter.autopilot-min-score или пост рубрики с autopilot = true
     * (у рубрик нет источника и оценки). Ручные посты без оценки — нет.
     */
    public boolean isAutopilotCandidate(PostQueue post) {
        if (!isAutopilotEnabled()) return false;
        if (post.getAiScore() != null) {
            return post.getAiScore() >= properties.getAutopilotMinScore();
        }
        return topicCategoryService.findById(post.getCategoryId())
                .map(TopicCategory::isAutopilot)
                .orElse(false);
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
        return TelegramKeyboard.builder()
                .row(
                        TelegramKeyboard.button("✅ В очередь", data(QUEUE_CMD, postId)),
                        TelegramKeyboard.button("⚡ Сейчас", data(NOW_CMD, postId))
                )
                .row(
                        TelegramKeyboard.button("🔁 Другое фото", data(PHOTO_CMD, postId)),
                        TelegramKeyboard.button("🎨 Сгенерировать", data(IMAGE_CMD, postId))
                )
                .row(
                        TelegramKeyboard.button("✂️ Короче", data(SHORT_CMD, postId)),
                        TelegramKeyboard.button("🚫 Без картинки", data(NO_IMAGE_CMD, postId))
                )
                .row(TelegramKeyboard.button("❌ Удалить", data(DELETE_CMD, postId)))
                .build();
    }

    // у опроса нет текста и картинки — править нечего: в очередь, сейчас или удалить
    public static InlineKeyboardMarkup pollKeyboard(Long postId) {
        return TelegramKeyboard.builder()
                .row(
                        TelegramKeyboard.button("✅ В очередь", data(QUEUE_CMD, postId)),
                        TelegramKeyboard.button("⚡ Сейчас", data(NOW_CMD, postId))
                )
                .row(TelegramKeyboard.button("❌ Удалить", data(DELETE_CMD, postId)))
                .build();
    }

    private static String data(String cmd, Long postId) {
        return Command.POSTER_REVIEW.getCommandText() + TelegramBot.DEFAULT_DELIMETER + cmd
                + TelegramBot.DEFAULT_DELIMETER + postId;
    }
}
