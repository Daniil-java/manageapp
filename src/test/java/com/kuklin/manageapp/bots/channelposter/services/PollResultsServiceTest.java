package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.components.PollResultsGenerator;
import com.kuklin.manageapp.bots.channelposter.components.PostPublisher;
import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import com.kuklin.manageapp.bots.channelposter.entities.ChannelPoll;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PollResultsServiceTest {

    private final ChannelPollService pollService = mock(ChannelPollService.class);
    private final PostQueueService postQueueService = mock(PostQueueService.class);
    private final TopicCategoryService topicCategoryService = mock(TopicCategoryService.class);
    private final PollResultsGenerator generator = mock(PollResultsGenerator.class);
    private final PostPublisher publisher = mock(PostPublisher.class);
    private final ChannelPosterProperties properties = new ChannelPosterProperties();
    private final ChannelPosterTelegramBot bot = mock(ChannelPosterTelegramBot.class);
    private final ChannelPosterBotKeyComponent keys = mock(ChannelPosterBotKeyComponent.class);
    private final ChannelFeedbackService feedbackService = mock(ChannelFeedbackService.class);
    private final PollResultsService service = new PollResultsService(
            pollService, postQueueService, topicCategoryService, generator, publisher, properties, bot, keys,
            feedbackService);

    @BeforeEach
    void setUp() {
        properties.setPollResultsMinVoters(3);
        when(keys.getChannelId()).thenReturn(-100L);
        when(keys.getAdminIds()).thenReturn(List.of(1L));
        when(topicCategoryService.getTopicCategoryByTopicTypeOrNull(TopicCategory.TopicType.QUIZ_ANSWER))
                .thenReturn(Optional.of(new TopicCategory().setId(9L)));
        when(postQueueService.save(any())).thenAnswer(inv -> inv.getArgument(0, PostQueue.class).setId(70L));
    }

    @Test
    void statsShowPercentBarsAndCorrectAnswer() {
        assertThat(PollResultsService.buildStats(quiz(List.of(6, 3, 1), 10))).isEqualTo("""
                <b>📊 Итоги квиза</b>
                «Где больше белка?»

                ✅ <b>Творог</b> — 60%
                ▓▓▓▓▓▓░░░░
                ▫️ Яблоко — 30%
                ▓▓▓░░░░░░░
                ▫️ Огурец &amp; соль — 10%
                ▓░░░░░░░░░

                Ответили: 10 · правильно — 60%""");
    }

    @Test
    void regularPollStatsHaveNoCorrectMark() {
        ChannelPoll poll = quiz(List.of(1, 2, 0), 3).setType(ChannelPoll.Type.REGULAR).setCorrectOptionId(null);

        assertThat(PollResultsService.buildStats(poll))
                .startsWith("<b>📊 Итоги опроса</b>")
                .contains("• Творог — 33%")
                .contains("• Яблоко — 67%")
                .endsWith("Ответили: 3")
                .doesNotContain("✅");
    }

    @Test
    void percentsSurviveMissingVotes() {
        assertThat(PollResultsService.getPercents(quiz(null, 0))).containsExactly(0, 0, 0);
        assertThat(PollResultsService.getBar(100)).isEqualTo("▓▓▓▓▓▓▓▓▓▓");
        assertThat(PollResultsService.getBar(0)).isEqualTo("░░░░░░░░░░");
    }

    @Test
    void publishesResultsAsReplyAndMarksPoll() {
        ChannelPoll poll = quiz(List.of(6, 3, 1), 10);
        when(pollService.getDueForResults()).thenReturn(List.of(poll));
        when(generator.generateComment(eq(poll), contains("Творог: 60% (правильный)"))).thenReturn("Комментарий");
        when(publisher.publishReply(eq(-100L), any(), eq(50))).thenReturn(61);

        assertThat(service.publishDueResults()).isEqualTo(1);

        verify(publisher).publishReply(eq(-100L), argThat(post ->
                post.getCategoryId().equals(9L) && post.getTextContent().endsWith("\n\nКомментарий")), eq(50));
        verify(postQueueService).save(argThat(post ->
                post.getStatus() == PostQueue.PostQueueStatus.SENT && post.getTgMessageId() == 61));
        verify(pollService).markResultsSent(poll, 70L);
    }

    @Test
    void withoutAiCommentQuizFallsBackToExplanation() {
        ChannelPoll poll = quiz(List.of(6, 3, 1), 10);
        when(pollService.getDueForResults()).thenReturn(List.of(poll));
        when(generator.generateComment(any(), any())).thenReturn(null);
        when(publisher.publishReply(any(), any(), any())).thenReturn(61);

        service.publishDueResults();

        verify(publisher).publishReply(any(), argThat(post ->
                post.getTextContent().endsWith("💬 В твороге 16 г на 100 г")), any());
    }

    @Test
    void tooFewVotersSkipResultsAndTellAdmins() {
        ChannelPoll poll = quiz(List.of(1, 1, 0), 2);
        when(pollService.getDueForResults()).thenReturn(List.of(poll));

        assertThat(service.publishDueResults()).isZero();

        verify(pollService).markResultsSent(poll, null);
        verify(bot).sendReturnedMessage(eq(1L), contains("ответили 2"));
        verifyNoInteractions(publisher, generator);
    }

    @Test
    void telegramRefusalLeavesPollForRetry() {
        ChannelPoll poll = quiz(List.of(6, 3, 1), 10);
        when(pollService.getDueForResults()).thenReturn(List.of(poll));
        when(publisher.publishReply(any(), any(), any())).thenReturn(null);

        assertThat(service.publishDueResults()).isZero();

        verify(pollService, never()).markResultsSent(any(), any());
        verify(postQueueService, never()).save(any());
    }

    @Test
    void guessCaloriesResultsLinkToBot() {
        ChannelPoll poll = quiz(List.of(6, 3, 1), 10).setType(ChannelPoll.Type.CALORIES).setPostQueueId(44L);
        when(pollService.getDueForResults()).thenReturn(List.of(poll));
        when(generator.generateComment(any(), any())).thenReturn("Комментарий");
        when(feedbackService.getBotLink(44L)).thenReturn("https://t.me/calorydairy_bot?start=zch44");
        when(publisher.publishReply(any(), any(), any())).thenReturn(61);

        service.publishDueResults();

        verify(publisher).publishReply(any(), argThat(post ->
                post.getTextContent().startsWith("<b>📊 Итоги «Угадай калорийность»</b>")
                        && post.getTextContent().endsWith("Комментарий\n\n" + ChannelFeedbackService.buildBotLinkLine(
                        "https://t.me/calorydairy_bot?start=zch44"))), eq(50));
    }

    @Test
    void deletedPollGetsResultsAsPlainPost() {
        ChannelPoll poll = quiz(List.of(6, 3, 1), 10);
        when(pollService.getDueForResults()).thenReturn(List.of(poll));
        when(publisher.publishReply(any(), any(), eq(50))).thenReturn(null);
        when(publisher.publishReply(any(), any(), isNull())).thenReturn(62);

        assertThat(service.publishDueResults()).isEqualTo(1);

        verify(pollService).markResultsSent(poll, 70L);
    }

    @Test
    void givesUpAfterADayAndTellsAdmins() {
        ChannelPoll poll = quiz(List.of(6, 3, 1), 10)
                .setResultsDueAt(Instant.now().minus(PollResultsService.GIVE_UP_AFTER).minusSeconds(60));
        when(pollService.getDueForResults()).thenReturn(List.of(poll));

        assertThat(service.publishDueResults()).isZero();

        verify(pollService).markResultsSent(poll, null);
        verify(bot).sendReturnedMessage(eq(1L), contains("бросаю попытки"));
        verifyNoInteractions(generator, publisher);
    }

    @Test
    void oneBrokenPollDoesNotBlockOthers() {
        ChannelPoll broken = quiz(List.of(6, 3, 1), 10).setId(1L);
        ChannelPoll fine = quiz(List.of(6, 3, 1), 10).setId(2L);
        when(pollService.getDueForResults()).thenReturn(List.of(broken, fine));
        when(generator.generateComment(eq(broken), any())).thenThrow(new IllegalStateException("AI"));
        when(publisher.publishReply(any(), any(), any())).thenReturn(61);

        assertThat(service.publishDueResults()).isEqualTo(1);

        verify(pollService).markResultsSent(fine, 70L);
        verify(pollService, never()).markResultsSent(eq(broken), any());
    }

    private static ChannelPoll quiz(List<Integer> votes, int total) {
        return new ChannelPoll().setId(3L).setType(ChannelPoll.Type.QUIZ).setTgMessageId(50)
                .setQuestion("Где больше белка?").setOptions(List.of("Творог", "Яблоко", "Огурец & соль"))
                .setCorrectOptionId(0).setExplanation("В твороге 16 г на 100 г")
                .setOptionVotes(votes).setTotalVoters(total);
    }
}
