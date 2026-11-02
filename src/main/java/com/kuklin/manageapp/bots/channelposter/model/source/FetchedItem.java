package com.kuklin.manageapp.bots.channelposter.model.source;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * Материал, который вернул парсер источника (ещё не сохранён).
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class FetchedItem {
    private String externalId;
    private String url;
    private String title;
    private String summary;
    private String content;
    private Instant publishedAt;
    private Integer score;
}
