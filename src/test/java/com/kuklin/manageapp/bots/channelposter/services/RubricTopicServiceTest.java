package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.components.RubricContentGenerator;
import com.kuklin.manageapp.bots.channelposter.entities.RubricTopic;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.repositories.RubricTopicRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RubricTopicServiceTest {

    private final RubricTopicRepository repository = mock(RubricTopicRepository.class);
    private final RubricContentGenerator generator = mock(RubricContentGenerator.class);
    private final RubricTopicService service = new RubricTopicService(
            repository, generator, mock(TopicCategoryService.class));

    private static final Instant NOW = Instant.now();

    @Test
    void picksTopicFromSectionThatWasNotUsedLongest() {
        List<RubricTopic> used = List.of(
                used("сахар", NOW.minus(Duration.ofDays(1))),
                used("белок", NOW.minus(Duration.ofDays(10))),
                used("белок", NOW.minus(Duration.ofDays(3))));
        List<RubricTopic> fresh = List.of(
                fresh(1L, "сахар"),
                fresh(2L, "белок"),
                fresh(3L, "сон"),
                fresh(4L, "сон"));

        // «сон» ещё не выходил — он первый, внутри раздела самая старая тема
        assertThat(RubricTopicService.pickNext(fresh, used)).map(RubricTopic::getId).contains(3L);
        // без «сна»: «белок» был 3 дня назад, «сахар» — вчера
        assertThat(RubricTopicService.pickNext(fresh.subList(0, 2), used)).map(RubricTopic::getId).contains(2L);
        assertThat(RubricTopicService.pickNext(List.of(), used)).isEmpty();
    }

    @Test
    void recyclesTopicsUsedOverAYearAgoOnlyWhenNoNewLeft() {
        when(repository.findAllByCategoryIdAndStatusOrderByIdAsc(2L, RubricTopic.Status.NEW))
                .thenReturn(List.of())
                .thenReturn(List.of(fresh(9L, "сон")));
        when(repository.updateStatusUsedBefore(eq(2L), any(), eq(RubricTopic.Status.USED), eq(RubricTopic.Status.NEW)))
                .thenReturn(1);

        assertThat(service.findNext(2L)).map(RubricTopic::getId).contains(9L);

        ArgumentCaptor<Instant> before = ArgumentCaptor.forClass(Instant.class);
        verify(repository).updateStatusUsedBefore(eq(2L), before.capture(), any(), any());
        assertThat(ChronoUnit.DAYS.between(before.getValue(), Instant.now())).isEqualTo(365);
    }

    @Test
    void emptyBankWithNothingToRecycle() {
        when(repository.findAllByCategoryIdAndStatusOrderByIdAsc(2L, RubricTopic.Status.NEW)).thenReturn(List.of());

        assertThat(service.findNext(2L)).isEmpty();
    }

    @Test
    void fillSkipsDuplicatesBlankAndForeignSections() {
        TopicCategory myth = new TopicCategory().setId(2L).setTopicType(TopicCategory.TopicType.MYTH);
        when(generator.getSections(TopicCategory.TopicType.MYTH)).thenReturn(RubricContentGenerator.SECTIONS);
        when(repository.findAllByCategoryId(2L)).thenReturn(List.of(fresh(1L, "сон").setText("Есть после шести — толстеть")));
        when(generator.generateTopics(TopicCategory.TopicType.MYTH, List.of("Есть после шести — толстеть")))
                .thenReturn(List.of(
                        new RubricContentGenerator.TopicIdea("режим питания", "есть после шести толстеть!"), // уже есть
                        new RubricContentGenerator.TopicIdea("Белок", "Белок вредит почкам"),
                        new RubricContentGenerator.TopicIdea("белок", "белок вредит почкам"),             // повтор в пачке
                        new RubricContentGenerator.TopicIdea("космос", "Сельдерей — минус калории"),
                        new RubricContentGenerator.TopicIdea("сон", "  ")));

        assertThat(service.fill(myth)).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RubricTopic>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(RubricTopic::getSection, RubricTopic::getText).containsExactly(
                org.assertj.core.groups.Tuple.tuple("белок", "Белок вредит почкам"),
                org.assertj.core.groups.Tuple.tuple(RubricContentGenerator.OTHER_SECTION, "Сельдерей — минус калории"));
        assertThat(saved.getValue()).allMatch(t -> t.getStatus() == RubricTopic.Status.NEW && t.getCategoryId() == 2L);
    }

    @Test
    void addRejectsExistingTopic() {
        when(repository.findAllByCategoryId(2L)).thenReturn(List.of(fresh(1L, "сон").setText("Ёжики едят на ночь")));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.add(2L, "ежики едят на ночь")).isNull();
        assertThat(service.add(2L, "  Кофе обезвоживает ")).extracting(RubricTopic::getText, RubricTopic::getSection)
                .containsExactly("Кофе обезвоживает", RubricContentGenerator.OTHER_SECTION);
    }

    @Test
    void offAndOnTouchOnlyNewAndDisabledTopics() {
        RubricTopic fresh = fresh(1L, "сон");
        RubricTopic used = used("сон", NOW).setId(2L);
        when(repository.findById(1L)).thenReturn(Optional.of(fresh));
        when(repository.findById(2L)).thenReturn(Optional.of(used));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.setEnabled(1L, false).getStatus()).isEqualTo(RubricTopic.Status.DISABLED);
        assertThat(service.setEnabled(1L, true).getStatus()).isEqualTo(RubricTopic.Status.NEW);
        assertThat(service.setEnabled(2L, false).getStatus()).isEqualTo(RubricTopic.Status.USED);
        assertThat(service.setEnabled(3L, false)).isNull();
    }

    private static RubricTopic fresh(Long id, String section) {
        return new RubricTopic().setId(id).setCategoryId(2L).setSection(section).setText("тема " + id)
                .setStatus(RubricTopic.Status.NEW);
    }

    private static RubricTopic used(String section, Instant usedAt) {
        return new RubricTopic().setCategoryId(2L).setSection(section).setText("было")
                .setStatus(RubricTopic.Status.USED).setUsedAt(usedAt);
    }
}
