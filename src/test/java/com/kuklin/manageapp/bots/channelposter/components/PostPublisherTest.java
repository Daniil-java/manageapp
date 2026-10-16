package com.kuklin.manageapp.bots.channelposter.components;

import com.kuklin.manageapp.bots.channelposter.entities.PostImage;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.services.PostImageService;
import com.kuklin.manageapp.bots.channelposter.services.TopicCategoryService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.services.TelegramService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostPublisherTest {

    private static final byte[] PHOTO = {1, 2, 3};

    private final ChannelPosterTelegramBot bot = mock(ChannelPosterTelegramBot.class);
    private final PostImageService postImageService = mock(PostImageService.class);
    private final TelegramService telegramService = mock(TelegramService.class);
    private final TopicCategoryService topicCategoryService = mock(TopicCategoryService.class);
    private final PostPublisher publisher = new PostPublisher(bot, postImageService, telegramService, topicCategoryService);

    @BeforeEach
    void setUp() {
        when(postImageService.getByPostQueueIdOrNull(1L)).thenReturn(new PostImage().setTgFileId("file"));
        when(telegramService.downloadFileOrNull(bot, "file")).thenReturn(PHOTO);
        when(topicCategoryService.findById(7L)).thenReturn(Optional.of(new TopicCategory().setHashtag("#миф")));
    }

    @Test
    void appendsEscapedSourceLink() {
        PostQueue post = new PostQueue()
                .setTextContent("<b>Белок</b> на завтрак\n")
                .setSourceUrl("https://example.com/a?x=1&y=\"2\"")
                .setSourceName("R&D <Lab>");

        assertThat(PostPublisher.renderText(post, null)).isEqualTo(
                "<b>Белок</b> на завтрак\n\n"
                        + "<a href=\"https://example.com/a?x=1&amp;y=&quot;2&quot;\">Источник: R&amp;D &lt;Lab&gt;</a>");
    }

    @Test
    void manualPostWithoutSource() {
        assertThat(PostPublisher.renderText(new PostQueue().setTextContent("Текст"), null)).isEqualTo("Текст");
    }

    @Test
    void hashtagGoesLastAfterSourceOrText() {
        PostQueue article = new PostQueue().setTextContent("Текст").setSourceUrl("https://e.com").setSourceName("E");
        assertThat(PostPublisher.renderText(article, "#наука"))
                .isEqualTo("Текст\n\n<a href=\"https://e.com\">Источник: E</a>\n#наука");
        assertThat(PostPublisher.renderText(new PostQueue().setTextContent("Текст"), "#миф"))
                .isEqualTo("Текст\n\n#миф");
    }

    @Test
    void visibleLengthSkipsTagsAndCountsEntitiesAsOneChar() {
        assertThat(PostPublisher.getVisibleLength("<b>Миф</b> &amp; <a href=\"x?a=1&amp;b\">да</a>")).isEqualTo(8);
        assertThat(PostPublisher.getVisibleLength("5 & 6")).isEqualTo(5);
    }

    @Test
    void shortPostGoesAsOnePhotoWithCaption() {
        Message sent = mock(Message.class);
        when(sent.getMessageId()).thenReturn(42);
        when(bot.sendHtmlPhoto(eq(100L), eq(PHOTO), anyString(), isNull())).thenReturn(sent);

        assertThat(publisher.publish(100L, post("Короткий миф"))).isEqualTo(42);

        verify(bot).sendHtmlPhoto(100L, PHOTO, "Короткий миф\n\n#миф", null);
        verify(bot, never()).sendPhotoMessage(anyLong(), any(byte[].class), anyString(), any(), any());
        verify(bot, never()).sendReturnedMessage(anyLong(), anyString(), any(), any());
    }

    @Test
    void longPostGoesAsPhotoThenText() {
        Message sent = mock(Message.class);
        when(sent.getMessageId()).thenReturn(43);
        when(bot.sendReturnedMessage(eq(100L), anyString(), isNull(), isNull())).thenReturn(sent);

        assertThat(publisher.publish(100L, post("<b>" + "а".repeat(PostPublisher.CAPTION_LIMIT) + "</b>"))).isEqualTo(43);

        verify(bot, never()).sendHtmlPhoto(anyLong(), any(), anyString(), any());
        verify(bot).sendPhotoMessage(eq(100L), eq(PHOTO), anyString(), isNull(), isNull());
    }

    @Test
    void rejectedCaptionFallsBackToPhotoAndText() {
        Message sent = mock(Message.class);
        when(sent.getMessageId()).thenReturn(44);
        when(bot.sendHtmlPhoto(anyLong(), any(), anyString(), any())).thenReturn(null);
        when(bot.sendReturnedMessage(eq(100L), anyString(), isNull(), isNull())).thenReturn(sent);

        assertThat(publisher.publish(100L, post("Короткий миф"))).isEqualTo(44);

        verify(bot).sendPhotoMessage(eq(100L), eq(PHOTO), anyString(), isNull(), isNull());
        verify(bot).sendReturnedMessage(100L, "Короткий миф\n\n#миф", null, null);
    }

    private static PostQueue post(String text) {
        return new PostQueue().setId(1L).setCategoryId(7L).setTextContent(text);
    }
}
