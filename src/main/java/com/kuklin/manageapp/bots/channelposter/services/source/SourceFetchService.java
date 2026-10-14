package com.kuklin.manageapp.bots.channelposter.services.source;

import com.kuklin.manageapp.bots.channelposter.components.source.SourceFetcher;
import com.kuklin.manageapp.bots.channelposter.components.source.SourceHttpClient;
import com.kuklin.manageapp.bots.channelposter.entities.source.ContentSource;
import com.kuklin.manageapp.bots.channelposter.model.source.FetchedItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Обходит источники: скачивает свежие материалы и складывает новые в channel_source_item.
 */
@Service
@Slf4j
public class SourceFetchService {

    private final Map<ContentSource.SourceType, SourceFetcher> fetchers = new EnumMap<>(ContentSource.SourceType.class);
    private final ContentSourceService sourceService;
    private final SourceItemService itemService;

    public SourceFetchService(List<SourceFetcher> fetchers,
                              ContentSourceService sourceService,
                              SourceItemService itemService) {
        fetchers.forEach(f -> this.fetchers.put(f.supportedType(), f));
        this.sourceService = sourceService;
        this.itemService = itemService;
    }

    public record FetchResult(int found, int added, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    /**
     * @return сколько новых материалов добавлено со всех активных источников
     */
    public int fetchAllActive() {
        int added = 0;
        for (ContentSource source : sourceService.getActive()) {
            FetchResult result = fetch(source);
            added += result.added();
            log.info("Source #{} {}: found {}, new {}{}", source.getId(), source.getName(),
                    result.found(), result.added(), result.ok() ? "" : ", error: " + result.error());
        }
        return added;
    }

    public FetchResult fetch(ContentSource source) {
        SourceFetcher fetcher = fetchers.get(source.getType());
        if (fetcher == null) {
            return new FetchResult(0, 0, "нет парсера для " + source.getType());
        }
        try {
            List<FetchedItem> items = fetcher.fetch(source);
            int added = itemService.saveNew(source.getId(), items);
            sourceService.markSuccess(source);
            return new FetchResult(items.size(), added, null);
        } catch (Exception e) {
            String error = SourceHttpClient.describe(e);
            log.warn("Source #{} {} fetch failed: {}", source.getId(), source.getName(), error);
            sourceService.markFailure(source, error);
            return new FetchResult(0, 0, error);
        }
    }

    /**
     * Проверка источника без сохранения — перед добавлением через бота.
     */
    public List<FetchedItem> preview(ContentSource source) throws Exception {
        SourceFetcher fetcher = fetchers.get(source.getType());
        if (fetcher == null) {
            throw new IllegalStateException("нет парсера для " + source.getType());
        }
        return fetcher.fetch(source);
    }
}
