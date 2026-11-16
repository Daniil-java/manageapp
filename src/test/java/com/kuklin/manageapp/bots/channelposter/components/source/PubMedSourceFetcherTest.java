package com.kuklin.manageapp.bots.channelposter.components.source;

import com.kuklin.manageapp.bots.channelposter.model.source.FetchedItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PubMedSourceFetcherTest {

    private static final String EFETCH = """
            <?xml version="1.0" ?>
            <!DOCTYPE PubmedArticleSet PUBLIC "-//NLM//DTD PubMedArticle, 1st January 2025//EN" "https://dtd.nlm.nih.gov/ncbi/pubmed/out/pubmed_250101.dtd">
            <PubmedArticleSet>
              <PubmedArticle>
                <MedlineCitation Status="MEDLINE" Owner="NLM">
                  <PMID Version="1">42847394</PMID>
                  <Article PubModel="Print">
                    <Journal><Title>The American journal of clinical nutrition</Title></Journal>
                    <ArticleTitle>High-protein breakfast and appetite: a randomized trial.</ArticleTitle>
                    <Abstract>
                      <AbstractText Label="BACKGROUND">Protein may increase satiety.</AbstractText>
                      <AbstractText Label="RESULTS">Participants ate 120 kcal less at lunch.</AbstractText>
                    </Abstract>
                    <PublicationTypeList>
                      <PublicationType UI="D016428">Journal Article</PublicationType>
                      <PublicationType UI="D016449">Randomized Controlled Trial</PublicationType>
                    </PublicationTypeList>
                  </Article>
                </MedlineCitation>
                <PubmedData>
                  <History>
                    <PubMedPubDate PubStatus="received"><Year>2026</Year><Month>5</Month><Day>1</Day></PubMedPubDate>
                    <PubMedPubDate PubStatus="pubmed"><Year>2026</Year><Month>10</Month><Day>8</Day></PubMedPubDate>
                  </History>
                </PubmedData>
              </PubmedArticle>
              <PubmedArticle>
                <MedlineCitation><PMID Version="1">1</PMID>
                  <Article><ArticleTitle>No abstract here</ArticleTitle></Article>
                </MedlineCitation>
              </PubmedArticle>
            </PubmedArticleSet>
            """;

    @Test
    void parsesArticlesWithAbstractOnly() {
        List<FetchedItem> items = PubMedSourceFetcher.parseArticles(EFETCH);

        assertThat(items).hasSize(1);
        FetchedItem item = items.get(0);
        assertThat(item.getExternalId()).isEqualTo("pubmed:42847394");
        assertThat(item.getUrl()).isEqualTo("https://pubmed.ncbi.nlm.nih.gov/42847394/");
        assertThat(item.getTitle()).isEqualTo("High-protein breakfast and appetite: a randomized trial.");
        assertThat(item.getSummary()).startsWith("[Randomized Controlled Trial] BACKGROUND: Protein may increase satiety.");
        assertThat(item.getContent())
                .contains("Журнал: The American journal of clinical nutrition")
                .contains("Тип публикации: Randomized Controlled Trial")
                .contains("RESULTS: Participants ate 120 kcal less at lunch.");
        assertThat(item.getPublishedAt()).isEqualTo(Instant.parse("2026-10-08T00:00:00Z"));
    }
}
