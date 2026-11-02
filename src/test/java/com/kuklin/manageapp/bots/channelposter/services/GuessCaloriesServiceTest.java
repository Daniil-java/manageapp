package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.DishDto;
import com.kuklin.manageapp.bots.channelposter.components.source.PixabayClient;
import com.kuklin.manageapp.bots.channelposter.entities.ChannelPoll;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.RubricTopic;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.model.PollContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GuessCaloriesServiceTest {

    private final PixabayClient stock = mock(PixabayClient.class);
    private final DishService dishService = mock(DishService.class);
    private final PostQueueService postQueueService = mock(PostQueueService.class);
    private final ChannelPollService pollService = mock(ChannelPollService.class);
    private final PostReviewService reviewService = mock(PostReviewService.class);
    private final RubricTopicService topicService = mock(RubricTopicService.class);
    private final GuessCaloriesService service = new GuessCaloriesService(
            stock, dishService, postQueueService, pollService, reviewService, topicService);

    private final TopicCategory category = new TopicCategory().setId(6L).setTopicType(TopicCategory.TopicType.GUESS_CALORIES);
    private final RubricTopic topic = new RubricTopic().setId(9L).setSection("основные блюда").setText("Паста карбонара");
    private final PixabayClient.StockPhoto photo = new PixabayClient.StockPhoto(
            1L, "https://pixabay.com/get/1.jpg", "https://pixabay.com/photos/pasta-1/", "Anna & Co");

    @BeforeEach
    void setUp() throws IOException {
        when(stock.search("Паста карбонара")).thenReturn(List.of(photo));
        when(postQueueService.save(any())).thenAnswer(inv -> inv.getArgument(0, PostQueue.class).setId(50L));
        when(reviewService.attachImageFromUrl(any(), eq(photo.imageUrl()))).thenReturn(true);
    }

    @Test
    void quizHasFourAscendingOptionsWithBotEstimateAsAnswer() {
        GuessCaloriesService.Totals totals = new GuessCaloriesService.Totals(643, 25, 30, 70, 350);

        for (long seed = 0; seed < 20; seed++) {
            PollContent quiz = GuessCaloriesService.buildQuiz("Паста карбонара", totals, new Random(seed));

            assertThat(quiz.isValid(true)).isTrue();
            assertThat(quiz.options()).hasSize(4).doesNotHaveDuplicates();
            assertThat(quiz.options().get(quiz.correctOptionId())).isEqualTo("≈ 640 ккал");
            List<Integer> values = quiz.options().stream()
                    .map(o -> Integer.parseInt(o.replace("≈ ", "").replace(" ккал", ""))).toList();
            assertThat(values).isSorted();
        }
        PollContent quiz = GuessCaloriesService.buildQuiz("Паста карбонара", totals, new Random(1));
        assertThat(quiz.question()).isEqualTo("Сколько калорий в порции на фото выше? («Паста карбонара»)");
        assertThat(quiz.explanation()).isEqualTo("Бот @calorydairy_bot насчитал ≈640 ккал: Б 25 · Ж 30 · У 70 г, порция ≈350 г");
    }

    @Test
    void answerPositionVaries() {
        GuessCaloriesService.Totals totals = new GuessCaloriesService.Totals(500, 1, 1, 1, 0);
        Random random = new Random(7);
        Set<Integer> positions = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            positions.add(GuessCaloriesService.buildQuiz("x", totals, random).correctOptionId());
        }
        assertThat(positions).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void captionCreditsPhotographer() {
        assertThat(GuessCaloriesService.buildCaption("Паста <карбонара>", photo)).isEqualTo("""
                <b>🍽 Угадай калорийность</b>
                «Паста &lt;карбонара&gt;» — сколько калорий в этой порции? Ответ — в квизе ниже, итоги завтра вечером.

                <i>Фото: <a href="https://pixabay.com/photos/pasta-1/">Anna &amp; Co</a> / Pixabay</i>""");
    }

    @Test
    void createsPostWithStockPhotoAndCaloriesQuiz() {
        when(dishService.recognizePhotoForChannelOrNull(photo.imageUrl(), "Паста карбонара"))
                .thenReturn(List.of(dish(500, 300), dish(143, 50)));

        PostQueue post = service.createPost(category, topic);

        assertThat(post.getCategoryId()).isEqualTo(6L);
        assertThat(post.getTitle()).isEqualTo("Угадай калорийность: Паста карбонара");
        verify(pollService).create(eq(50L), argThat(q -> q.options().get(q.correctOptionId()).equals("≈ 640 ккал")),
                eq(ChannelPoll.Type.CALORIES));
        verify(topicService).markUsed(topic, 50L);
        verify(reviewService).prepareAndSendToAdmins(post);
    }

    @Test
    void noPhotosDisablesTopic() throws IOException {
        when(stock.search("Паста карбонара")).thenReturn(List.of());

        assertThat(service.createPost(category, topic)).isNull();

        verify(topicService).setEnabled(9L, false);
        verifyNoInteractions(dishService, postQueueService);
    }

    @Test
    void failedRecognitionLeavesTopicNew() {
        when(dishService.recognizePhotoForChannelOrNull(any(), any())).thenReturn(null);

        assertThat(service.createPost(category, topic)).isNull();

        verify(topicService, never()).markUsed(any(), any());
        verify(topicService, never()).setEnabled(any(), anyBoolean());
        verifyNoInteractions(postQueueService);
    }

    @Test
    void tooFewCaloriesAreNotAGame() {
        when(dishService.recognizePhotoForChannelOrNull(any(), any())).thenReturn(List.of(dish(5, 250)));

        assertThat(service.createPost(category, topic)).isNull();
        verifyNoInteractions(postQueueService);
    }

    @Test
    void photoRejectedByTelegramRemovesPost() {
        when(dishService.recognizePhotoForChannelOrNull(any(), any())).thenReturn(List.of(dish(400, 300)));
        when(reviewService.attachImageFromUrl(any(), any())).thenReturn(false);

        assertThat(service.createPost(category, topic)).isNull();

        verify(postQueueService).removePost(50L);
        verify(topicService, never()).markUsed(any(), any());
        verify(reviewService, never()).prepareAndSendToAdmins(any());
    }

    private static DishDto dish(int kcal, int weight) {
        DishDto dto = new DishDto();
        dto.setCalories(kcal);
        dto.setProteins(kcal == 500 ? 20 : 5);
        dto.setFats(kcal == 500 ? 25 : 5);
        dto.setCarbohydrates(kcal == 500 ? 60 : 10);
        dto.setWeight(weight);
        return dto;
    }
}
