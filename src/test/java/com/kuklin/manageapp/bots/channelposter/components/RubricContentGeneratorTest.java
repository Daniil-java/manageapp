package com.kuklin.manageapp.bots.channelposter.components;

import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.channelposter.entities.RubricTopic;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.model.PollContent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RubricContentGeneratorTest {

    @Test
    void postPromptCarriesTopicAndSharedRules() {
        RubricTopic topic = new RubricTopic().setSection("режим питания").setText("Есть после 6 — 100% толстеть");

        assertThat(RubricContentGenerator.buildPostPrompt(TopicCategory.TopicType.MYTH, topic))
                .contains("Рубрика «Миф или правда»")
                .contains("Тема поста (раздел «режим питания»): Есть после 6 — 100% толстеть")
                .contains(PostPromptParts.MARKUP_RULES)
                .contains(PostPromptParts.IMAGE_RULES)
                .doesNotContain("%SECTION%")
                .doesNotContain("%TOPIC%");
    }

    @Test
    void topicsPromptListsExistingTopicsSectionsAndCount() {
        String prompt = RubricContentGenerator.buildTopicsPrompt(TopicCategory.TopicType.FACT,
                List.of("Слизистая желудка обновляется за несколько дней", "Мозг тратит около 20% энергии"));

        assertThat(prompt)
                .contains("Рубрика «Факт»")
                .contains("- Слизистая желудка обновляется за несколько дней\n- Мозг тратит около 20% энергии")
                .contains(String.join(", ", RubricContentGenerator.SECTIONS))
                .contains("ровно " + RubricContentGenerator.TOPICS_PER_FILL + " тем")
                .doesNotContain("%COUNT%")
                .doesNotContain("%EXISTING%")
                .doesNotContain("%SECTIONS%");
    }

    @Test
    void parsesTopicsFromAiJson() {
        OpenAiProviderProcessor ai = mock(OpenAiProviderProcessor.class);
        when(ai.fetchJsonResponse(any(), any(), any(), any(), any())).thenReturn("""
                {"topics": [{"section": "сон", "text": "Недосып усиливает голод", "why": "лишнее поле"}]}""");
        RubricContentGenerator generator = new RubricContentGenerator(ai, mock(ChannelPosterBotKeyComponent.class));

        assertThat(generator.generateTopics(TopicCategory.TopicType.FACT, List.of()))
                .containsExactly(new RubricContentGenerator.TopicIdea("сон", "Недосып усиливает голод"));
    }

    @Test
    void brokenTopicsJsonGivesNothing() {
        OpenAiProviderProcessor ai = mock(OpenAiProviderProcessor.class);
        when(ai.fetchJsonResponse(any(), any(), any(), any(), any())).thenReturn("не json");
        RubricContentGenerator generator = new RubricContentGenerator(ai, mock(ChannelPosterBotKeyComponent.class));

        assertThat(generator.generateTopics(TopicCategory.TopicType.FACT, List.of())).isEmpty();
    }

    @Test
    void quizPromptIsPollShapedNotPost() {
        RubricTopic topic = new RubricTopic().setSection("белок").setText("Где больше белка: в твороге или в яблоке?");

        assertThat(RubricContentGenerator.buildPollPrompt(TopicCategory.TopicType.QUIZ, topic))
                .contains("Рубрика «Квиз»")
                .contains("\"correct_option_id\"")
                .contains("Тема (раздел «белок»): Где больше белка: в твороге или в яблоке?")
                .doesNotContain("image_prompt");
        assertThat(RubricContentGenerator.buildPostPrompt(TopicCategory.TopicType.QUIZ, topic)).isNull();
        assertThat(RubricContentGenerator.buildPollPrompt(TopicCategory.TopicType.MYTH, topic)).isNull();
    }

    @Test
    void shuffleKeepsCorrectAnswer() {
        PollContent quiz = new PollContent("Q", List.of("верно", "нет 1", "нет 2", "нет 3"), 0, "потому что");

        for (long seed = 0; seed < 20; seed++) {
            PollContent shuffled = RubricContentGenerator.shuffle(quiz, new Random(seed));
            assertThat(shuffled.options()).containsExactlyInAnyOrderElementsOf(quiz.options());
            assertThat(shuffled.options().get(shuffled.correctOptionId())).isEqualTo("верно");
        }
    }

    @Test
    void invalidPollFromAiIsDropped() {
        OpenAiProviderProcessor ai = mock(OpenAiProviderProcessor.class);
        // правильный вариант вне списка — Telegram не примет
        when(ai.fetchCreativeJsonResponse(any(), any(), any(), any(), any())).thenReturn("""
                {"question": "Q?", "options": ["a", "b"], "correct_option_id": 5, "explanation": "e"}""");
        RubricContentGenerator generator = new RubricContentGenerator(ai, mock(ChannelPosterBotKeyComponent.class));

        assertThat(generator.generatePoll(TopicCategory.TopicType.QUIZ,
                new RubricTopic().setSection("сон").setText("t"))).isNull();
    }

    @Test
    void pollLimits() {
        List<String> two = List.of("a", "b");
        assertThat(new PollContent("Q", two, null, null).isValid(false)).isTrue();
        assertThat(new PollContent("Q", two, null, null).isValid(true)).isFalse();
        assertThat(new PollContent("Q", List.of("a"), null, null).isValid(false)).isFalse();
        assertThat(new PollContent("Q", List.of("a", "a "), null, null).isValid(false)).isFalse();
        assertThat(new PollContent("Q", List.of("a", "b".repeat(101)), null, null).isValid(false)).isFalse();
        assertThat(new PollContent("Q", two, 1, "e".repeat(201)).isValid(true)).isFalse();
        assertThat(new PollContent("Q", two, 1, "e").isValid(true)).isTrue();
    }

    @Test
    void emptyBank() {
        assertThat(RubricContentGenerator.buildTopicsPrompt(TopicCategory.TopicType.MYTH, List.of()))
                .contains("пока пусто");
    }

    @Test
    void articlesAndResultsAreNotRubrics() {
        assertThat(RubricContentGenerator.buildTopicsPrompt(TopicCategory.TopicType.ARTICLE, List.of())).isNull();
        assertThat(RubricContentGenerator.buildTopicsPrompt(TopicCategory.TopicType.QUIZ_ANSWER, List.of())).isNull();
        assertThat(RubricContentGenerator.buildTopicsPrompt(TopicCategory.TopicType.POLL, List.of()))
                .contains("Рубрика «Опрос»");
    }
}
