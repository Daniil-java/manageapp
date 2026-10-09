package com.kuklin.manageapp.bots.channelposter.components.source;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleTextExtractorTest {

    private static final String PARAGRAPH = "Researchers at the University of Example found that eating more fiber at breakfast reduced hunger. ";

    @Test
    void takesParagraphsFromArticleBodyWithoutMenus() {
        String html = """
                <html><body>
                  <nav><ul><li>Home</li><li>Health news and more health news</li></ul></nav>
                  <div id="story_text">
                    <p>%s</p>
                    <p>%s</p>
                    <p>%s</p>
                    <p>%s</p>
                    <p>Share</p>
                  </div>
                  <footer><p>Copyright 2026 Example Media, all rights reserved worldwide.</p></footer>
                </body></html>
                """.formatted(PARAGRAPH, PARAGRAPH, PARAGRAPH, PARAGRAPH + "Last one.");

        String text = ArticleTextExtractor.extractFromHtml(html);

        assertThat(text).startsWith("Researchers at the University").endsWith("Last one.");
        assertThat(text).doesNotContain("Home", "Share", "Copyright");
        assertThat(text.split("\n")).hasSize(4);
    }

    @Test
    void fallsBackToBodyParagraphs() {
        String html = "<html><body><div class='x'>"
                + ("<p>" + PARAGRAPH + "</p>").repeat(5)
                + "</div></body></html>";

        assertThat(ArticleTextExtractor.extractFromHtml(html)).contains("fiber at breakfast");
    }

    @Test
    void tooShortPageIsNull() {
        assertThat(ArticleTextExtractor.extractFromHtml("<html><body><p>Please log in to continue reading.</p></body></html>"))
                .isNull();
    }
}
