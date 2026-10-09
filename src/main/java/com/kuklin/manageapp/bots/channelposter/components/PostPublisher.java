package com.kuklin.manageapp.bots.channelposter.components;

import com.kuklin.manageapp.bots.channelposter.entities.PostImage;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.services.PostImageService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.services.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.UUID;

/**
 * Отправка поста: картинка (если есть) отдельным сообщением, затем текст со ссылкой на источник.
 * Подпись к фото ограничена 1024 символами, поэтому текст идёт отдельно.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostPublisher {

    private final ChannelPosterTelegramBot bot;
    private final PostImageService postImageService;
    private final TelegramService telegramService;

    /**
     * @return id сообщения с текстом или null, если отправить не получилось
     */
    public Integer publish(Long chatId, PostQueue post) {
        return send(chatId, post, null, null, true);
    }

    public Integer send(Long chatId, PostQueue post, String footer, InlineKeyboardMarkup keyboard, boolean withImage) {
        PostImage image = withImage ? postImageService.getByPostQueueIdOrNull(post.getId()) : null;
        if (image != null && image.getTgFileId() != null) {
            byte[] bytes = telegramService.downloadFileOrNull(bot, image.getTgFileId());
            if (bytes != null) {
                bot.sendPhotoMessage(chatId, bytes, UUID.randomUUID().toString(), null, null);
            } else {
                log.warn("Post #{}: can't download image {}", post.getId(), image.getTgFileId());
            }
        }
        String text = renderText(post) + (footer == null ? "" : footer);
        Message message = bot.sendReturnedMessage(chatId, text, keyboard, null);
        return message == null ? null : message.getMessageId();
    }

    /**
     * Текст поста + ссылка на источник.
     */
    public static String renderText(PostQueue post) {
        String text = post.getTextContent() == null ? "" : post.getTextContent().trim();
        if (post.getSourceUrl() == null || post.getSourceUrl().isBlank()) {
            return text;
        }
        String name = post.getSourceName() == null || post.getSourceName().isBlank()
                ? "Источник"
                : "Источник: " + escapeHtml(post.getSourceName());
        return text + "\n\n<a href=\"" + escapeAttr(post.getSourceUrl()) + "\">" + name + "</a>";
    }

    static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttr(String s) {
        return escapeHtml(s).replace("\"", "&quot;");
    }
}
