package com.kuklin.manageapp.bots.channelposter.components;

import com.kuklin.manageapp.bots.channelposter.entities.ChannelPoll;
import com.kuklin.manageapp.bots.channelposter.entities.PostImage;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.services.ChannelFeedbackService;
import com.kuklin.manageapp.bots.channelposter.services.ChannelPollService;
import com.kuklin.manageapp.bots.channelposter.services.PostImageService;
import com.kuklin.manageapp.bots.channelposter.services.TopicCategoryService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.services.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Отправка поста. Влезает в подпись к фото (1024 символа) — одним сообщением: фото с подписью.
 * Длиннее — картинка отдельным сообщением, затем текст. В конце текста — ссылка на источник и хэштег рубрики.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostPublisher {

    // лимит Telegram на подпись к фото — в видимых символах, после разбора HTML
    static final int CAPTION_LIMIT = 1024;
    private static final Set<TopicCategory.TopicType> BOT_LINK_TYPES = Set.of(
            TopicCategory.TopicType.ARTICLE, TopicCategory.TopicType.MYTH, TopicCategory.TopicType.FACT);

    private final ChannelPosterTelegramBot bot;
    private final PostImageService postImageService;
    private final TelegramService telegramService;
    private final TopicCategoryService topicCategoryService;
    private final ChannelPollService pollService;
    private final ChannelFeedbackService feedbackService;

    /**
     * @return id сообщения с текстом или null, если отправить не получилось
     */
    public Integer publish(Long chatId, PostQueue post) {
        Optional<ChannelPoll> poll = pollService.findByPostQueueId(post.getId());
        if (poll.isPresent()) {
            Message lead = sendLeadPhoto(chatId, post);
            // угадайка без фото — вопрос «что на фото выше» без фото: не публикуем, пост уйдёт в FAILED
            if (lead == null && poll.get().getType() == ChannelPoll.Type.CALORIES) {
                log.warn("Post #{}: guess-calories photo not sent, poll skipped", post.getId());
                return null;
            }
            Message message = sendPoll(chatId, poll.get());
            if (message == null) {
                // квиз не ушёл — не оставляем в канале фото без вопроса
                if (lead != null) bot.sendDeleteMessage(chatId, lead.getMessageId());
                return null;
            }
            // по id опроса придут голоса, на сообщение ответят итоги
            pollService.markPublished(poll.get(), message.getPoll().getId(), message.getMessageId());
            return message.getMessageId();
        }
        return send(chatId, post, null, null, true);
    }

    /**
     * Итоги квиза или опроса — текстом в ответ на сообщение с опросом.
     *
     * @return id сообщения или null, если отправить не получилось
     */
    public Integer publishReply(Long chatId, PostQueue post, Integer replyToMessageId) {
        Message message = bot.sendReturnedMessage(chatId, render(post), null, replyToMessageId);
        return message == null ? null : message.getMessageId();
    }

    public Integer send(Long chatId, PostQueue post, String footer, InlineKeyboardMarkup keyboard, boolean withImage) {
        Optional<ChannelPoll> poll = pollService.findByPostQueueId(post.getId());
        if (poll.isPresent()) {
            return sendPollPreview(chatId, post, poll.get(), footer, keyboard);
        }
        PostImage image = withImage ? postImageService.getByPostQueueIdOrNull(post.getId()) : null;
        byte[] photo = getPhotoOrNull(post, image);
        // автор фото со стока — только если фото уходит вместе с постом
        String credit = photo == null ? null : buildPhotoCreditOrNull(image);
        String text = render(post, credit) + (footer == null ? "" : footer);

        if (photo != null && getVisibleLength(text) <= CAPTION_LIMIT) {
            Message message = bot.sendHtmlPhoto(chatId, photo, text, keyboard);
            if (message != null) {
                return message.getMessageId();
            }
            // подпись не приняли (например, HTML) — шлём двумя сообщениями, текст уйдёт хотя бы простым
            log.warn("Post #{}: photo with caption rejected, sending photo and text separately", post.getId());
        }
        if (photo != null) {
            bot.sendPhotoMessage(chatId, photo, UUID.randomUUID().toString(), null, null);
        }
        Message message = bot.sendReturnedMessage(chatId, text, keyboard, null);
        return message == null ? null : message.getMessageId();
    }

    // у угадайки перед квизом — фото блюда с подписью; у обычных квизов и опросов картинки нет
    /**
     * @return сообщение с фото или null — фото нет или Telegram не принял
     */
    private Message sendLeadPhoto(Long chatId, PostQueue post) {
        byte[] photo = getPhotoOrNull(post, postImageService.getByPostQueueIdOrNull(post.getId()));
        if (photo == null) return null;
        Message message = bot.sendHtmlPhoto(chatId, photo, render(post), null);
        if (message == null) {
            log.warn("Post #{}: lead photo of the poll rejected", post.getId());
        }
        return message;
    }

    private Message sendPoll(Long chatId, ChannelPoll poll) {
        return bot.sendPoll(chatId, poll.getQuestion(), poll.getOptions(),
                poll.isQuiz() ? poll.getCorrectOptionId() : null, poll.getExplanation());
    }

    // превью опроса: сам опрос (у админа он живой — можно ответить), под ним правильный ответ и кнопки
    private Integer sendPollPreview(Long chatId, PostQueue post, ChannelPoll poll, String footer,
                                    InlineKeyboardMarkup keyboard) {
        sendLeadPhoto(chatId, post);
        if (sendPoll(chatId, poll) == null) {
            log.warn("Poll #{}: Telegram didn't accept the poll", poll.getId());
        }
        String text = renderPollDetails(poll) + (footer == null ? "" : footer);
        Message message = bot.sendReturnedMessage(chatId, text.trim(), keyboard, null);
        return message == null ? null : message.getMessageId();
    }

    static String renderPollDetails(ChannelPoll poll) {
        if (!poll.isQuiz()) {
            return "📊 Опрос мнений — итоги выйдут на следующий день";
        }
        return "✅ Правильный ответ: " + escapeHtml(poll.getOptions().get(poll.getCorrectOptionId()))
                + "\n💬 " + escapeHtml(poll.getExplanation());
    }

    private byte[] getPhotoOrNull(PostQueue post, PostImage image) {
        if (image == null || image.getTgFileId() == null) {
            return null;
        }
        byte[] bytes = telegramService.downloadFileOrNull(bot, image.getTgFileId());
        if (bytes == null) {
            log.warn("Post #{}: can't download image {}", post.getId(), image.getTgFileId());
        }
        return bytes;
    }

    private String render(PostQueue post) {
        return render(post, null);
    }

    private String render(PostQueue post, String photoCredit) {
        TopicCategory category = topicCategoryService.findById(post.getCategoryId()).orElse(null);
        return renderText(post, category == null ? null : category.getHashtag(), getBotLinkOrNull(post, category),
                photoCredit);
    }

    /**
     * «Фото: автор / Pixabay» — правила Pixabay просят показывать источник. У угадайки автор уже в тексте поста,
     * у её картинки stockAuthor не заполнен — второй раз не подписываем.
     */
    static String buildPhotoCreditOrNull(PostImage image) {
        if (image == null || image.getStockAuthor() == null || image.getStockPageUrl() == null) return null;
        return "<i>Фото: <a href=\"" + escapeAttr(image.getStockPageUrl()) + "\">" + escapeHtml(image.getStockAuthor())
                + "</a> / Pixabay</i>";
    }

    // ссылка на калорийного бота — в каждом CTA_EVERY-м текстовом посте, чтобы не превращать канал в рекламу
    private String getBotLinkOrNull(PostQueue post, TopicCategory category) {
        if (post.getId() == null || category == null || category.getTopicType() == null) return null;
        if (!BOT_LINK_TYPES.contains(category.getTopicType())) return null;
        if (post.getId() % ChannelFeedbackService.CTA_EVERY != 0) return null;
        // ссылка — приятное дополнение: не создалась UTM-запись — пост выходит без неё, а не падает
        try {
            return feedbackService.getBotLink(post.getId());
        } catch (Exception e) {
            log.warn("Post #{}: bot link not created: {}", post.getId(), e.getMessage());
            return null;
        }
    }

    public static String renderText(PostQueue post, String hashtag, String botLink) {
        return renderText(post, hashtag, botLink, null);
    }

    /**
     * Текст поста, затем хвост строками: автор фото, ссылка на источник, ссылка на бота, хэштег рубрики.
     */
    public static String renderText(PostQueue post, String hashtag, String botLink, String photoCredit) {
        String text = post.getTextContent() == null ? "" : post.getTextContent().trim();
        List<String> tail = new ArrayList<>();
        if (photoCredit != null) {
            tail.add(photoCredit);
        }
        if (post.getSourceUrl() != null && !post.getSourceUrl().isBlank()) {
            String name = post.getSourceName() == null || post.getSourceName().isBlank()
                    ? "Источник"
                    : "Источник: " + escapeHtml(post.getSourceName());
            tail.add("<a href=\"" + escapeAttr(post.getSourceUrl()) + "\">" + name + "</a>");
        }
        if (botLink != null) {
            tail.add(ChannelFeedbackService.buildBotLinkLine(botLink));
        }
        if (hashtag != null && !hashtag.isBlank()) {
            tail.add(escapeHtml(hashtag.trim()));
        }
        return tail.isEmpty() ? text : text + "\n\n" + String.join("\n", tail);
    }

    /**
     * Длина текста, как её считает Telegram: без HTML-тегов, сущности (&amp; …) — один символ.
     */
    static int getVisibleLength(String html) {
        int length = 0;
        boolean inTag = false;
        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            if (inTag) {
                if (c == '>') inTag = false;
            } else if (c == '<') {
                inTag = true;
            } else if (c == '&') {
                int semicolon = html.indexOf(';', i);
                if (semicolon > i && semicolon - i <= 8) {
                    i = semicolon;
                }
                length++;
            } else {
                length++;
            }
        }
        return length;
    }

    public static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttr(String s) {
        return escapeHtml(s).replace("\"", "&quot;");
    }
}
