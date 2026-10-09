package com.kuklin.manageapp.bots.channelposter.services.source;

import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import com.kuklin.manageapp.bots.channelposter.entities.source.SourceItem;
import com.kuklin.manageapp.bots.channelposter.model.source.FetchedItem;
import com.kuklin.manageapp.bots.channelposter.repositories.source.SourceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SourceItemService {

    private final SourceItemRepository repository;
    // channelposter.max-item-age: старше — уже не новость, не сохраняем и снимаем с фильтра/генерации
    private final ChannelPosterProperties properties;

    /**
     * Сохраняет новые материалы источника (дубли по externalId и слишком старые отбрасываются).
     *
     * @return сколько добавлено
     */
    @Transactional
    public int saveNew(Long sourceId, List<FetchedItem> fetched) {
        if (fetched == null || fetched.isEmpty()) return 0;

        Instant oldest = Instant.now().minus(properties.getMaxItemAge());
        // внутри одной пачки тоже бывают дубли (одна статья в нескольких рубриках)
        Map<String, FetchedItem> unique = new LinkedHashMap<>();
        for (FetchedItem item : fetched) {
            if (item.getPublishedAt() != null && item.getPublishedAt().isBefore(oldest)) continue;
            unique.putIfAbsent(item.getExternalId(), item);
        }
        if (unique.isEmpty()) return 0;

        Set<String> existing = new HashSet<>(repository.findExistingExternalIds(unique.keySet()));
        List<SourceItem> toSave = unique.values().stream()
                .filter(i -> !existing.contains(i.getExternalId()))
                .map(i -> new SourceItem()
                        .setSourceId(sourceId)
                        .setExternalId(i.getExternalId())
                        .setUrl(i.getUrl())
                        .setTitle(i.getTitle())
                        .setSummary(i.getSummary())
                        .setContent(i.getContent())
                        .setPublishedAt(i.getPublishedAt())
                        .setScore(i.getScore())
                        .setStatus(SourceItem.Status.NEW))
                .toList();
        repository.saveAll(toSave);
        return toSave.size();
    }

    public List<SourceItem> getNewForFilter(int limit) {
        return repository.findAllByStatusOrderByPublishedAtDesc(SourceItem.Status.NEW, PageRequest.of(0, limit));
    }

    public List<SourceItem> getBestApproved(int limit) {
        return repository.findAllByStatusOrderByAiScoreDescPublishedAtDesc(SourceItem.Status.APPROVED, PageRequest.of(0, limit));
    }

    /**
     * Снимает устаревшие NEW/APPROVED, чтобы не тратить на них AI.
     */
    @Transactional
    public int expireOld() {
        Instant before = Instant.now().minus(properties.getMaxItemAge());
        return repository.expire(SourceItem.Status.NEW, SourceItem.Status.REJECTED, before, "устарело")
                + repository.expire(SourceItem.Status.APPROVED, SourceItem.Status.REJECTED, before, "устарело");
    }

    public SourceItem save(SourceItem item) {
        return repository.save(item);
    }

    public List<SourceItem> saveAll(List<SourceItem> items) {
        return repository.saveAll(items);
    }

    public long countByStatus(SourceItem.Status status) {
        return repository.countByStatus(status);
    }

    public long countParsedSince(Instant after) {
        return repository.countByParsedAtAfter(after);
    }

    public long countParsedSince(SourceItem.Status status, Instant after) {
        return repository.countByStatusAndParsedAtAfter(status, after);
    }

    /**
     * sourceId → (статус → количество) за период.
     */
    public Map<Long, Map<SourceItem.Status, Long>> statsBySource(Instant after) {
        Map<Long, Map<SourceItem.Status, Long>> result = new HashMap<>();
        for (Object[] row : repository.countBySourceAndStatusAfter(after)) {
            result.computeIfAbsent((Long) row[0], k -> new EnumMap<>(SourceItem.Status.class))
                    .put((SourceItem.Status) row[1], (Long) row[2]);
        }
        return result;
    }
}
