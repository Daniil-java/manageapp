package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.components.RubricContentGenerator;
import com.kuklin.manageapp.bots.channelposter.entities.RubricTopic;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.repositories.RubricTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Банк тем рубрик: следующая тема для поста, пополнение через AI, ручное управление (/topics).
 * Темы не повторяются: использованная тема снова в ходу только через год и только когда новые кончились.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RubricTopicService {

    /** Новых тем меньше — банк пополняется перед следующим постом. */
    public static final int MIN_NEW_TOPICS = 10;
    static final Duration RECYCLE_AFTER = Duration.ofDays(365);
    private static final int MAX_TEXT_LENGTH = 300;

    private final RubricTopicRepository repository;
    private final RubricContentGenerator generator;
    private final TopicCategoryService topicCategoryService;

    /** Активные рубрики с банком тем — те, для которых есть генератор. */
    public List<TopicCategory> getRubrics() {
        return topicCategoryService.getActive().stream()
                .filter(c -> generator.supports(c.getTopicType()))
                .toList();
    }

    /**
     * Следующая тема: из раздела, который дольше всех не выходил, внутри раздела — самая старая.
     * Новые кончились — возвращаем в оборот темы, использованные больше года назад.
     */
    @Transactional
    public Optional<RubricTopic> findNext(Long categoryId) {
        List<RubricTopic> fresh = repository.findAllByCategoryIdAndStatusOrderByIdAsc(categoryId, RubricTopic.Status.NEW);
        if (fresh.isEmpty()) {
            int recycled = repository.updateStatusUsedBefore(categoryId, Instant.now().minus(RECYCLE_AFTER),
                    RubricTopic.Status.USED, RubricTopic.Status.NEW);
            if (recycled == 0) return Optional.empty();
            log.info("Rubric category {}: {} old topics recycled", categoryId, recycled);
            fresh = repository.findAllByCategoryIdAndStatusOrderByIdAsc(categoryId, RubricTopic.Status.NEW);
        }
        List<RubricTopic> used = repository.findAllByCategoryIdAndStatusOrderByIdAsc(categoryId, RubricTopic.Status.USED);
        return pickNext(fresh, used);
    }

    static Optional<RubricTopic> pickNext(List<RubricTopic> fresh, List<RubricTopic> used) {
        // раздел → когда последний раз выходил пост (нет — раздел ещё не выходил, он первый)
        Map<String, Instant> lastUsed = new HashMap<>();
        for (RubricTopic topic : used) {
            if (topic.getUsedAt() == null) continue;
            lastUsed.merge(topic.getSection(), topic.getUsedAt(), (a, b) -> a.isAfter(b) ? a : b);
        }
        // fresh отсортирован по id: при равенстве разделов берём самую старую тему
        return fresh.stream().min(Comparator.comparing(
                (RubricTopic t) -> lastUsed.getOrDefault(t.getSection(), Instant.EPOCH)));
    }

    public RubricTopic markUsed(RubricTopic topic, Long postQueueId) {
        return repository.save(topic
                .setStatus(RubricTopic.Status.USED)
                .setUsedAt(Instant.now())
                .setPostQueueId(postQueueId));
    }

    public long countNew(Long categoryId) {
        return repository.countByCategoryIdAndStatus(categoryId, RubricTopic.Status.NEW);
    }

    public Map<RubricTopic.Status, Long> countByStatus(Long categoryId) {
        Map<RubricTopic.Status, Long> counts = new EnumMap<>(RubricTopic.Status.class);
        for (RubricTopic.Status status : RubricTopic.Status.values()) {
            counts.put(status, repository.countByCategoryIdAndStatus(categoryId, status));
        }
        return counts;
    }

    public List<RubricTopic> getNew(Long categoryId) {
        return repository.findAllByCategoryIdAndStatusOrderByIdAsc(categoryId, RubricTopic.Status.NEW);
    }

    /**
     * Пополняет банк темами от AI. Дубли (с точностью до регистра и знаков препинания) и пустые отсеиваются.
     *
     * @return сколько тем добавлено
     */
    public int fill(TopicCategory category) {
        List<RubricTopic> all = repository.findAllByCategoryId(category.getId());
        List<String> existing = all.stream().map(RubricTopic::getText).toList();
        List<RubricContentGenerator.TopicIdea> ideas = generator.generateTopics(category.getTopicType(), existing);

        Set<String> seen = new HashSet<>();
        existing.forEach(text -> seen.add(normalize(text)));
        List<RubricTopic> toSave = new ArrayList<>();
        for (RubricContentGenerator.TopicIdea idea : ideas) {
            if (idea == null || idea.text() == null || idea.text().isBlank()) continue;
            String text = idea.text().trim();
            if (text.length() > MAX_TEXT_LENGTH || !seen.add(normalize(text))) continue;
            String section = idea.section() == null ? "" : idea.section().trim().toLowerCase();
            toSave.add(newTopic(category.getId(),
                    RubricContentGenerator.SECTIONS.contains(section) ? section : RubricContentGenerator.OTHER_SECTION,
                    text));
        }
        repository.saveAll(toSave);
        log.info("Rubric {}: {} topics added from {} AI ideas", category.getTopicType(), toSave.size(), ideas.size());
        return toSave.size();
    }

    /**
     * Своя тема админа.
     *
     * @return тема или null, если такая уже есть
     */
    public RubricTopic add(Long categoryId, String text) {
        String trimmed = text.trim();
        boolean exists = repository.findAllByCategoryId(categoryId).stream()
                .anyMatch(t -> normalize(t.getText()).equals(normalize(trimmed)));
        if (exists) return null;
        return repository.save(newTopic(categoryId, RubricContentGenerator.OTHER_SECTION,
                trimmed.length() > MAX_TEXT_LENGTH ? trimmed.substring(0, MAX_TEXT_LENGTH) : trimmed));
    }

    /**
     * Выключить тему или вернуть выключенную в оборот. Использованную не трогаем: пост по ней уже есть.
     *
     * @return тема или null, если такой нет
     */
    public RubricTopic setEnabled(Long id, boolean enabled) {
        RubricTopic topic = repository.findById(id).orElse(null);
        if (topic == null) return null;
        if (enabled && topic.getStatus() == RubricTopic.Status.DISABLED) {
            topic.setStatus(RubricTopic.Status.NEW);
        } else if (!enabled && topic.getStatus() == RubricTopic.Status.NEW) {
            topic.setStatus(RubricTopic.Status.DISABLED);
        }
        return repository.save(topic);
    }

    private static RubricTopic newTopic(Long categoryId, String section, String text) {
        return new RubricTopic()
                .setCategoryId(categoryId)
                .setSection(section)
                .setText(text)
                .setStatus(RubricTopic.Status.NEW);
    }

    // «Есть после 6 — толстеть!» и «есть после 6 толстеть» — одна тема
    static String normalize(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toLowerCase().replace('ё', 'е').toCharArray()) {
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }
}
