package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.components.RubricContentGenerator;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.RubricTopic;
import com.kuklin.manageapp.bots.channelposter.entities.ScheduleSlot;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.model.AiGeneratedContent;
import com.kuklin.manageapp.bots.channelposter.model.PollContent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RubricPostServiceTest {

    private final ScheduleSlotService scheduleSlotService = mock(ScheduleSlotService.class);
    private final PostQueueService postQueueService = mock(PostQueueService.class);
    private final RubricContentGenerator generator = mock(RubricContentGenerator.class);
    private final RubricTopicService topicService = mock(RubricTopicService.class);
    private final ChannelPollService pollService = mock(ChannelPollService.class);
    private final PostReviewService reviewService = mock(PostReviewService.class);
    private final ChannelPosterTelegramBot bot = mock(ChannelPosterTelegramBot.class);
    private final ChannelPosterBotKeyComponent keys = mock(ChannelPosterBotKeyComponent.class);
    private final RubricPostService service = new RubricPostService(
            scheduleSlotService, postQueueService, generator, topicService, pollService, reviewService, bot, keys);

    private final TopicCategory myth = new TopicCategory().setId(2L).setName("Миф или правда")
            .setTopicType(TopicCategory.TopicType.MYTH);
    private final RubricTopic topic = new RubricTopic().setId(5L).setCategoryId(2L)
            .setSection("режим питания").setText("Есть после шести — толстеть").setStatus(RubricTopic.Status.NEW);

    @BeforeEach
    void setUp() {
        when(topicService.getRubrics()).thenReturn(List.of(myth));
        // миф каждый день — на неделю вперёд всегда 6–7 слотов
        when(scheduleSlotService.getSlotByTopicCategoryIdAndIsActiveTrue(2L))
                .thenReturn(List.of(slot(null, "14:00")));
        when(postQueueService.save(any())).thenAnswer(inv -> inv.getArgument(0, PostQueue.class).setId(77L));
        when(topicService.countNew(2L)).thenReturn(30L);
        when(topicService.findNext(2L)).thenReturn(Optional.of(topic));
        when(keys.getAdminIds()).thenReturn(List.of(1L));
    }

    @Test
    void countsOnlyFutureSlotsOnTheirWeekdays() {
        // пятница 16.10.2026, 15:00
        LocalDateTime now = LocalDateTime.of(2026, 10, 16, 15, 0);
        List<ScheduleSlot> slots = List.of(
                slot(1, "14:00"),      // пн
                slot(5, "14:00"),      // пт — сегодня, но 14:00 уже прошло
                slot(5, "19:00"),      // пт — сегодня, ещё впереди
                slot(null, "09:00"));  // каждый день

        // пн 19.10 + пт 19:00 сегодня + 09:00 на шесть следующих дней (сегодняшние 09:00 прошли)
        assertThat(RubricPostService.countSlotsAhead(slots, now, 7)).isEqualTo(1 + 1 + 6);
    }

    @Test
    void writesPostOnNextTopicAndMarksItUsed() {
        when(postQueueService.countInPipelineByCategory(2L)).thenReturn(1L);
        when(generator.generate(TopicCategory.TopicType.MYTH, topic)).thenReturn(content());

        assertThat(service.generateDue()).isEqualTo(1);

        verify(reviewService).prepareAndSendToAdmins(argThat(post ->
                post.getCategoryId().equals(2L)
                        && post.getTitle().equals("Есть после шести")
                        && post.getStatus() == PostQueue.PostQueueStatus.TEXT_GENERATED
                        && post.getSourceUrl() == null));
        verify(topicService).markUsed(topic, 77L);
        verify(topicService, never()).fill(any());
    }

    @Test
    void skipsRubricWhenPipelineCoversTheWeek() {
        when(postQueueService.countInPipelineByCategory(2L)).thenReturn(7L);

        assertThat(service.generateDue()).isZero();
        verify(topicService, never()).findNext(any());
        verifyNoInteractions(generator, reviewService);
    }

    @Test
    void aiFailureLeavesTopicNew() {
        when(postQueueService.countInPipelineByCategory(2L)).thenReturn(0L);
        when(generator.generate(any(), any())).thenReturn(null);

        assertThat(service.generateDue()).isZero();
        verify(postQueueService, never()).save(any());
        verify(topicService, never()).markUsed(any(), any());
        verifyNoInteractions(reviewService);
    }

    @Test
    void refillsLowBankBeforePostAndTellsAdmins() {
        when(postQueueService.countInPipelineByCategory(2L)).thenReturn(0L);
        when(topicService.countNew(2L)).thenReturn(3L);
        when(topicService.fill(myth)).thenReturn(40);
        when(generator.generate(any(), any())).thenReturn(content());

        assertThat(service.generateDue()).isEqualTo(1);

        verify(topicService).fill(myth);
        verify(bot).sendReturnedMessage(eq(1L), contains("добавлено 40"));
    }

    @Test
    void emptyBankAndNoNewTopicsWarnsAndCreatesNothing() {
        when(postQueueService.countInPipelineByCategory(2L)).thenReturn(0L);
        when(topicService.countNew(2L)).thenReturn(0L);
        when(topicService.fill(myth)).thenReturn(0);
        when(topicService.findNext(2L)).thenReturn(Optional.empty());

        assertThat(service.generateDue()).isZero();

        verify(bot).sendReturnedMessage(eq(1L), contains("пуст"));
        verifyNoInteractions(generator);
    }

    @Test
    void quizRubricMakesPollPostWithoutImageText() {
        TopicCategory quiz = new TopicCategory().setId(4L).setName("Квиз").setTopicType(TopicCategory.TopicType.QUIZ);
        when(topicService.getRubrics()).thenReturn(List.of(quiz));
        when(scheduleSlotService.getSlotByTopicCategoryIdAndIsActiveTrue(4L)).thenReturn(List.of(slot(null, "14:00")));
        when(topicService.countNew(4L)).thenReturn(30L);
        when(topicService.findNext(4L)).thenReturn(Optional.of(topic));
        PollContent poll = new PollContent("Где больше белка?", List.of("Творог", "Яблоко"), 0, "Так");
        when(generator.generatePoll(TopicCategory.TopicType.QUIZ, topic)).thenReturn(poll);

        assertThat(service.generateDue()).isEqualTo(1);

        verify(pollService).create(77L, poll, true);
        verify(topicService).markUsed(topic, 77L);
        verify(reviewService).prepareAndSendToAdmins(argThat(post ->
                post.getTitle().equals("Где больше белка?") && post.getImageDescription() == null));
        verify(generator, never()).generate(any(), any());
    }

    private static AiGeneratedContent content() {
        AiGeneratedContent content = new AiGeneratedContent();
        content.setPostTitle("Есть после шести");
        content.setPostText("<b>«Есть после шести — толстеть»</b>\n❌ Миф");
        content.setImagePrompt("plate");
        return content;
    }

    private static ScheduleSlot slot(Integer dayOfWeek, String time) {
        return new ScheduleSlot().setDayOfWeek(dayOfWeek).setPostTime(LocalTime.parse(time));
    }
}
