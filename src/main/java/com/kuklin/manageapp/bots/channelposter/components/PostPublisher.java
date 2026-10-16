package com.kuklin.manageapp.bots.channelposter.components;

import com.kuklin.manageapp.bots.channelposter.entities.PostImage;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.services.PostImageService;
import com.kuklin.manageapp.bots.channelposter.services.TopicCategoryService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.services.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

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

    private final ChannelPosterTelegramBot bot;
    private final PostImageService postImageService;
    private final TelegramService telegramService;
    private final TopicCategoryService topicCategoryService;

    /**
     * @return id сообщения с текстом или null, если отправить не получилось
     */
    public Integer publish(Long chatId, PostQueue post) {
        return send(chatId, post, null, null, true);
    }

    public Integer send(Long chatId, PostQueue post, String footer, InlineKeyboardMarkup keyboard, boolean withImage) {
        byte[] photo = withImage ? getPhotoOrNull(post) : null;
        String text = renderText(post, getHashtagOrNull(post)) + (footer == null ? "" : footer);

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

    private byte[] getPhotoOrNull(PostQueue post) {
        PostImage image = postImageService.getByPostQueueIdOrNull(post.getId());
        if (image == null || image.getTgFileId() == null) {
            return null;
        }
        byte[] bytes = telegramService.downloadFileOrNull(bot, image.getTgFileId());
        if (bytes == null) {
            log.warn("Post #{}: can't download image {}", post.getId(), image.getTgFileId());
        }
        return bytes;
    }

    private String getHashtagOrNull(PostQueue post) {
        return topicCategoryService.findById(post.getCategoryId())
                .map(TopicCategory::getHashtag)
                .orElse(null);
    }

    /**
     * Текст поста + ссылка на источник + хэштег рубрики.
     */
    public static String renderText(PostQueue post, String hashtag) {
        StringBuilder sb = new StringBuilder(post.getTextContent() == null ? "" : post.getTextContent().trim());
        if (post.getSourceUrl() != null && !post.getSourceUrl().isBlank()) {
            String name = post.getSourceName() == null || post.getSourceName().isBlank()
                    ? "Источник"
                    : "Источник: " + escapeHtml(post.getSourceName());
            sb.append("\n\n<a href=\"").append(escapeAttr(post.getSourceUrl())).append("\">").append(name).append("</a>");
        }
        if (hashtag != null && !hashtag.isBlank()) {
            sb.append(post.getSourceUrl() == null || post.getSourceUrl().isBlank() ? "\n\n" : "\n")
                    .append(escapeHtml(hashtag.trim()));
        }
        return sb.toString();
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

    static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttr(String s) {
        return escapeHtml(s).replace("\"", "&quot;");
    }
}
