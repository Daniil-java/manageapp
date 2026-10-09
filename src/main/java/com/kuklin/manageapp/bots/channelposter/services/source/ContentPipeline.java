package com.kuklin.manageapp.bots.channelposter.services.source;

import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.source.SourceItem;
import com.kuklin.manageapp.bots.channelposter.services.PostQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Конвейер канала: сбор → AI-фильтр → генерация постов на проверку.
 * Шаги не пересекаются: ручной запуск из бота и расписание делят один замок.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentPipeline {

    private final SourceFetchService fetchService;
    private final SourceItemFilterService filterService;
    private final SourceItemPostService postService;
    private final SourceItemService itemService;
    private final PostQueueService postQueueService;
    private final ChannelPosterProperties properties;

    private final ReentrantLock lock = new ReentrantLock();

    public record RunResult(int fetched, int approved, int posts) {
    }

    public int fetch() {
        return locked(() -> {
            long backlog = itemService.countByStatus(SourceItem.Status.NEW)
                    + itemService.countByStatus(SourceItem.Status.APPROVED);
            if (backlog >= properties.getMaxBacklog()) {
                log.info("Content pipeline: backlog {} — fetch skipped", backlog);
                return 0;
            }
            return fetchService.fetchAllActive();
        }, 0);
    }

    public int filter() {
        return locked(filterService::filterBatch, 0);
    }

    /**
     * Делает посты из лучших одобренных материалов, пока конвейер не заполнен.
     */
    public int generate() {
        return locked(this::generateUnlocked, 0);
    }

    /**
     * Всё сразу — для кнопки «запустить сейчас» в боте.
     */
    public RunResult runAll() {
        return locked(() -> {
            int fetched = fetchService.fetchAllActive();
            int approved = 0;
            // фильтр берёт по 40 материалов — после первого сбора их бывает больше
            for (int i = 0; i < 4; i++) {
                if (itemService.countByStatus(SourceItem.Status.NEW) == 0) break;
                approved += filterService.filterBatch();
            }
            return new RunResult(fetched, approved, generateUnlocked());
        }, null);
    }

    public boolean isBusy() {
        return lock.isLocked();
    }

    private int generateUnlocked() {
        long inPipeline = postQueueService.countInPipeline();
        int toCreate = (int) Math.min(properties.getPostsPerRun(), properties.getPipelineTarget() - inPipeline);
        if (toCreate <= 0) {
            log.info("Content pipeline: {} posts in review/queue — generation skipped", inPipeline);
            return 0;
        }
        int created = 0;
        List<SourceItem> candidates = itemService.getBestApproved(toCreate * 3);
        for (SourceItem item : candidates) {
            if (created >= toCreate) break;
            PostQueue post = postService.createPost(item);
            if (post != null) created++;
        }
        log.info("Content pipeline: {} posts created from {} candidates", created, candidates.size());
        return created;
    }

    private <T> T locked(Supplier<T> action, T busyValue) {
        if (!lock.tryLock()) {
            log.info("Content pipeline is busy — step skipped");
            return busyValue;
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
