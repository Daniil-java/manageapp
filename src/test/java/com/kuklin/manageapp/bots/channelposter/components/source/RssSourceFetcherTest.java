package com.kuklin.manageapp.bots.channelposter.components.source;

import com.kuklin.manageapp.bots.channelposter.model.source.FetchedItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RssSourceFetcherTest {

    private static final String RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>Nutrition Research News -- ScienceDaily</title>
                <image><title>logo</title><url>https://example.com/logo.png</url></image>
                <item>
                  <title>Old news</title>
                  <link>https://www.sciencedaily.com/releases/2026/10/old.htm</link>
                  <description>Old &amp; boring</description>
                  <pubDate>Mon, 05 Oct 2026 09:00:00 EDT</pubDate>
                </item>
                <item>
                  <title>Vitamin D2 may lower vitamin D3 &amp; more</title>
                  <link>https://www.sciencedaily.com/releases/2026/10/261007232940.htm</link>
                  <guid isPermaLink="false">sd-261007232940</guid>
                  <description><![CDATA[<p>Not all vitamin D supplements are <b>created equal</b>.</p>]]></description>
                  <content:encoded><![CDATA[<p>Short</p>]]></content:encoded>
                  <pubDate>Thu, 08 Oct 2026 10:16:25 EDT</pubDate>
                </item>
              </channel>
            </rss>
            """;

    private static final String ATOM = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>The Conversation – Nutrition</title>
              <entry>
                <id>tag:theconversation.com,2011:article/123</id>
                <published>2026-10-07T12:00:00Z</published>
                <updated>2026-10-08T12:00:00Z</updated>
                <link rel="alternate" type="text/html" href="https://theconversation.com/protein-123"/>
                <title>How much protein do you really need?</title>
                <summary>Short teaser</summary>
                <content type="html">&lt;p&gt;First paragraph about protein and muscle that is long enough to count as real content for the post.&lt;/p&gt;&lt;p&gt;Second paragraph with more details about studies and recommendations for adults.&lt;/p&gt;</content>
              </entry>
            </feed>
            """;

    @Test
    void parsesRssNewestFirstWithNamedTimeZones() {
        List<FetchedItem> items = RssSourceFetcher.parseFeed(RSS, 10);

        assertThat(items).hasSize(2);
        FetchedItem first = items.get(0);
        assertThat(first.getTitle()).isEqualTo("Vitamin D2 may lower vitamin D3 & more");
        assertThat(first.getUrl()).isEqualTo("https://www.sciencedaily.com/releases/2026/10/261007232940.htm");
        assertThat(first.getExternalId()).isEqualTo(first.getUrl());
        assertThat(first.getSummary()).isEqualTo("Not all vitamin D supplements are created equal.");
        // content:encoded не длиннее анонса — не храним
        assertThat(first.getContent()).isNull();
        assertThat(first.getPublishedAt()).isEqualTo(Instant.parse("2026-10-08T14:16:25Z"));
    }

    @Test
    void respectsMaxItems() {
        assertThat(RssSourceFetcher.parseFeed(RSS, 1))
                .extracting(FetchedItem::getTitle)
                .containsExactly("Vitamin D2 may lower vitamin D3 & more");
    }

    @Test
    void parsesAtomWithHtmlContent() {
        List<FetchedItem> items = RssSourceFetcher.parseFeed(ATOM, 10);

        assertThat(items).hasSize(1);
        FetchedItem item = items.get(0);
        assertThat(item.getExternalId()).isEqualTo("tag:theconversation.com,2011:article/123");
        assertThat(item.getUrl()).isEqualTo("https://theconversation.com/protein-123");
        assertThat(item.getSummary()).isEqualTo("Short teaser");
        assertThat(item.getContent()).startsWith("First paragraph about protein").contains("\nSecond paragraph");
        assertThat(item.getPublishedAt()).isEqualTo(Instant.parse("2026-10-07T12:00:00Z"));
    }

    @Test
    void notAFeed() {
        assertThat(RssSourceFetcher.parseFeed("<html><body>Login</body></html>", 10)).isEmpty();
        assertThat(RssSourceFetcher.looksLikeFeed("<html><body>Login</body></html>")).isFalse();
        assertThat(RssSourceFetcher.looksLikeFeed(ATOM)).isTrue();
    }
}
