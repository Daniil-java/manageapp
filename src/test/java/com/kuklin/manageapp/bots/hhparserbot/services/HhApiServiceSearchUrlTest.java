package com.kuklin.manageapp.bots.hhparserbot.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HhApiServiceSearchUrlTest {

    @Test
    void addsSortByDateAndPageToHhRuLink() {
        assertThat(HhApiService.searchPageUrl("https://hh.ru/search/vacancy?text=java&area=1", 0))
                .isEqualTo("https://hh.kz/search/vacancy?text=java&area=1&order_by=publication_time&page=0");
    }

    @Test
    void replacesExistingSortAndPage() {
        assertThat(HhApiService.searchPageUrl(
                "https://spb.hh.ru/search/vacancy?order_by=relevance&text=java&page=4&area=2", 1))
                .isEqualTo("https://hh.kz/search/vacancy?text=java&area=2&order_by=publication_time&page=1");
    }

    @Test
    void linkWithoutQuery() {
        assertThat(HhApiService.searchPageUrl("https://hh.kz/search/vacancy", 2))
                .isEqualTo("https://hh.kz/search/vacancy?order_by=publication_time&page=2");
    }

    @Test
    void doesNotTouchParamsWithSimilarNames() {
        assertThat(HhApiService.withQueryParam("https://hh.kz/s?xpage=7&page=3&page_size=5", "page", "0"))
                .isEqualTo("https://hh.kz/s?xpage=7&page_size=5&page=0");
    }
}
