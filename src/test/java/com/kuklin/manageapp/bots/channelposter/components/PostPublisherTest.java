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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.polls.Poll;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;
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
    private final ChannelPollService pollService = mock(ChannelPollService.class);
    private final ChannelFeedbackService feedbackService = mock(ChannelFeedbackService.class);
    private final PostPublisher publisher = new PostPublisher(
            bot, postImageService, telegramService, topicCategoryService, pollService, feedbackService);

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

        assertThat(PostPublisher.renderText(post, null, null)).isEqualTo(
                "<b>Белок</b> на завтрак\n\n"
                        + "<a href=\"https://example.com/a?x=1&amp;y=&quot;2&quot;\">Источник: R&amp;D &lt;Lab&gt;</a>");
    }

    @Test
    void manualPostWithoutSource() {
        assertThat(PostPublisher.renderText(new PostQueue().setTextContent("Текст"), null, null)).isEqualTo("Текст");
    }

    @Test
    void hashtagGoesLastAfterSourceOrText() {
        PostQueue article = new PostQueue().setTextContent("Текст").setSourceUrl("https://e.com").setSourceName("E");
        assertThat(PostPublisher.renderText(article, "#наука", null))
                .isEqualTo("Текст\n\n<a href=\"https://e.com\">Источник: E</a>\n#наука");
        assertThat(PostPublisher.renderText(new PostQueue().setTextContent("Текст"), "#миф", null,
                PostPublisher.buildPhotoCreditOrNull(new PostImage().setStockAuthor("Anna").setStockPageUrl("https://p/1"))))
                .isEqualTo("Текст\n\n<i>Фото: <a href=\"https://p/1\">Anna</a> / Pixabay</i>\n#миф");
        // у угадайки автор уже в тексте — картинка без stockAuthor не подписывается
        assertThat(PostPublisher.buildPhotoCreditOrNull(new PostImage().setTgFileId("f"))).isNull();
        assertThat(PostPublisher.renderText(new PostQueue().setTextContent("Текст"), "#миф", null))
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

    @Test
    void pollPostGoesAsTelegramQuizAndRemembersPollId() {
        ChannelPoll quiz = quiz();
        when(pollService.findByPostQueueId(1L)).thenReturn(Optional.of(quiz));
        when(postImageService.getByPostQueueIdOrNull(1L)).thenReturn(null);
        Message sent = mock(Message.class);
        Poll tgPoll = mock(Poll.class);
        when(tgPoll.getId()).thenReturn("tg-poll");
        when(sent.getPoll()).thenReturn(tgPoll);
        when(sent.getMessageId()).thenReturn(50);
        when(bot.sendPoll(100L, "Где больше белка?", List.of("Творог", "Яблоко"), 0, "В твороге 16 г на 100 г"))
                .thenReturn(sent);

        assertThat(publisher.publish(100L, post("Где больше белка?"))).isEqualTo(50);

        verify(pollService).markPublished(quiz, "tg-poll", 50);
        // у обычного квиза картинки нет — только опрос
        verify(bot, never()).sendHtmlPhoto(anyLong(), any(), anyString(), any());
    }

    @Test
    void regularPollIsSentWithoutCorrectAnswer() {
        ChannelPoll poll = quiz().setType(ChannelPoll.Type.REGULAR).setCorrectOptionId(null).setExplanation(null);
        when(pollService.findByPostQueueId(1L)).thenReturn(Optional.of(poll));

        assertThat(publisher.publish(100L, post("Q"))).isNull();

        verify(bot).sendPoll(100L, "Где больше белка?", List.of("Творог", "Яблоко"), null, null);
        verify(pollService, never()).markPublished(any(), any(), any());
    }

    @Test
    void pollPreviewShowsPollThenAnswerWithButtons() {
        when(pollService.findByPostQueueId(1L)).thenReturn(Optional.of(quiz()));
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();

        publisher.send(200L, post("Q"), "\n\n— футер", keyboard, true);

        verify(bot).sendPoll(eq(200L), anyString(), anyList(), eq(0), anyString());
        verify(bot).sendReturnedMessage(200L,
                "✅ Правильный ответ: Творог\n💬 В твороге 16 г на 100 г\n\n— футер", keyboard, null);
    }

    @Test
    void resultsReplyToPollMessage() {
        when(topicCategoryService.findById(9L)).thenReturn(Optional.of(new TopicCategory().setHashtag("#итоги")));
        Message sent = mock(Message.class);
        when(sent.getMessageId()).thenReturn(61);
        when(bot.sendReturnedMessage(100L, "Итоги\n\n#итоги", null, 50)).thenReturn(sent);

        assertThat(publisher.publishReply(100L, new PostQueue().setCategoryId(9L).setTextContent("Итоги"), 50))
                .isEqualTo(61);
    }

    @Test
    void everyThirdArticleLinksToBotWithPostCode() {
        when(topicCategoryService.findById(8L)).thenReturn(Optional.of(
                new TopicCategory().setTopicType(TopicCategory.TopicType.ARTICLE).setHashtag("#наука")));
        when(feedbackService.getBotLink(3L)).thenReturn("https://t.me/calorydairy_bot?start=zch3");
        PostQueue third = new PostQueue().setId(3L).setCategoryId(8L).setTextContent("Текст");
        PostQueue fourth = new PostQueue().setId(4L).setCategoryId(8L).setTextContent("Текст");

        publisher.send(100L, third, null, null, false);
        publisher.send(100L, fourth, null, null, false);

        verify(bot).sendReturnedMessage(100L, "Текст\n\n"
                + ChannelFeedbackService.buildBotLinkLine("https://t.me/calorydairy_bot?start=zch3") + "\n#наука", null, null);
        verify(bot).sendReturnedMessage(100L, "Текст\n\n#наука", null, null);
        verify(feedbackService, never()).getBotLink(4L);
    }

    @Test
    void guessCaloriesGoesAsPhotoWithCaptionThenQuiz() {
        ChannelPoll poll = quiz().setType(ChannelPoll.Type.CALORIES);
        when(pollService.findByPostQueueId(1L)).thenReturn(Optional.of(poll));
        Message sent = mock(Message.class);
        Poll tgPoll = mock(Poll.class);
        when(tgPoll.getId()).thenReturn("tg");
        when(sent.getPoll()).thenReturn(tgPoll);
        when(sent.getMessageId()).thenReturn(52);
        when(bot.sendPoll(anyLong(), anyString(), anyList(), any(), any())).thenReturn(sent);
        when(bot.sendHtmlPhoto(anyLong(), any(), anyString(), any())).thenReturn(mock(Message.class));

        assertThat(publisher.publish(100L, post("<b>Угадай</b>"))).isEqualTo(52);

        var order = inOrder(bot);
        order.verify(bot).sendHtmlPhoto(100L, PHOTO, "<b>Угадай</b>\n\n#миф", null);
        order.verify(bot).sendPoll(eq(100L), anyString(), anyList(), eq(0), anyString());
        verify(pollService).markPublished(poll, "tg", 52);
    }

    @Test
    void guessWithoutPhotoIsNotPublished() {
        when(pollService.findByPostQueueId(1L)).thenReturn(Optional.of(quiz().setType(ChannelPoll.Type.CALORIES)));
        when(bot.sendHtmlPhoto(anyLong(), any(), anyString(), any())).thenReturn(null);

        assertThat(publisher.publish(100L, post("<b>Угадай</b>"))).isNull();

        verify(bot, never()).sendPoll(anyLong(), anyString(), anyList(), any(), any());
    }

    @Test
    void rejectedQuizRemovesItsPhotoFromChannel() {
        when(pollService.findByPostQueueId(1L)).thenReturn(Optional.of(quiz().setType(ChannelPoll.Type.CALORIES)));
        Message lead = mock(Message.class);
        when(lead.getMessageId()).thenReturn(51);
        when(bot.sendHtmlPhoto(anyLong(), any(), anyString(), any())).thenReturn(lead);
        when(bot.sendPoll(anyLong(), anyString(), anyList(), any(), any())).thenReturn(null);

        assertThat(publisher.publish(100L, post("<b>Угадай</b>"))).isNull();

        verify(bot).sendDeleteMessage(100L, 51);
        verify(pollService, never()).markPublished(any(), any(), any());
    }

    @Test
    void botLinkFailureDoesNotBreakPost() {
        when(topicCategoryService.findById(8L)).thenReturn(Optional.of(
                new TopicCategory().setTopicType(TopicCategory.TopicType.ARTICLE)));
        when(feedbackService.getBotLink(3L)).thenThrow(new IllegalStateException("db down"));

        publisher.send(100L, new PostQueue().setId(3L).setCategoryId(8L).setTextContent("Текст"), null, null, false);

        verify(bot).sendReturnedMessage(100L, "Текст", null, null);
    }

    private static ChannelPoll quiz() {
        return new ChannelPoll().setId(3L).setPostQueueId(1L).setType(ChannelPoll.Type.QUIZ)
                .setQuestion("Где больше белка?").setOptions(List.of("Творог", "Яблоко"))
                .setCorrectOptionId(0).setExplanation("В твороге 16 г на 100 г");
    }

    private static PostQueue post(String text) {
        return new PostQueue().setId(1L).setCategoryId(7L).setTextContent(text);
    }
}
