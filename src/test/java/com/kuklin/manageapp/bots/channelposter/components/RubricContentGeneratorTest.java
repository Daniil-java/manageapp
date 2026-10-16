package com.kuklin.manageapp.bots.channelposter.components;

import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.channelposter.entities.RubricTopic;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void emptyBank() {
        assertThat(RubricContentGenerator.buildTopicsPrompt(TopicCategory.TopicType.MYTH, List.of()))
                .contains("пока пусто");
    }

    @Test
    void onlyMythAndFactAreRubrics() {
        assertThat(RubricContentGenerator.buildTopicsPrompt(TopicCategory.TopicType.ARTICLE, List.of())).isNull();
        assertThat(RubricContentGenerator.buildPostPrompt(TopicCategory.TopicType.QUIZ, new RubricTopic())).isNull();
    }
}
