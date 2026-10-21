package com.kuklin.manageapp.bots.channelposter.components.source;

import com.kuklin.manageapp.bots.channelposter.entities.source.ContentSource;
import com.kuklin.manageapp.bots.channelposter.model.source.FetchedItem;

import java.util.List;

/**
 * Парсер одного типа источников. Кидает исключение, если источник недоступен —
 * ошибку сохраняем в источник и показываем админу.
 */
public interface SourceFetcher {

    List<FetchedItem> fetch(ContentSource source) throws Exception;

    ContentSource.SourceType supportedType();
}
