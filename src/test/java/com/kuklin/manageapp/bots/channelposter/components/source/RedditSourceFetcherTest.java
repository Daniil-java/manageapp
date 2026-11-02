package com.kuklin.manageapp.bots.channelposter.components.source;

import com.kuklin.manageapp.bots.channelposter.model.source.FetchedItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedditSourceFetcherTest {

    private final RedditSourceFetcher fetcher = new RedditSourceFetcher(null, null);

    private static final String LONG_TEXT = "I tracked my protein for 12 weeks. ".repeat(10);

    private static final String LISTING = """
            {"kind":"Listing","data":{"children":[
              {"kind":"t3","data":{"name":"t3_good","title":"Lost 20 kg with protein-first meals","is_self":true,
                "selftext":"%s","permalink":"/r/loseit/comments/good/lost/","url":"https://www.reddit.com/r/loseit/comments/good/lost/",
                "score":540,"created_utc":1791500000,"domain":"self.loseit"}},
              {"kind":"t3","data":{"name":"t3_link","title":"New RCT on fasting","is_self":false,"selftext":"",
                "url":"https://www.bmj.com/content/123","permalink":"/r/nutrition/comments/link/","score":120,
                "created_utc":1791500000,"domain":"bmj.com"}},
              {"kind":"t3","data":{"name":"t3_sticky","title":"Weekly thread","is_self":true,"selftext":"%s","stickied":true,"score":900,"created_utc":1791500000}},
              {"kind":"t3","data":{"name":"t3_img","title":"My meal","is_self":false,"url":"https://i.redd.it/x.jpg","domain":"i.redd.it","post_hint":"image","score":800,"created_utc":1791500000}},
              {"kind":"t3","data":{"name":"t3_low","title":"Low score","is_self":true,"selftext":"%s","score":3,"created_utc":1791500000}},
              {"kind":"t3","data":{"name":"t3_short","title":"Is rice ok?","is_self":true,"selftext":"Question?","score":300,"created_utc":1791500000}}
            ]}}
            """.formatted(LONG_TEXT, LONG_TEXT, LONG_TEXT);

    @Test
    void keepsTextPostsAndLinksDropsNoise() throws Exception {
        List<FetchedItem> items = fetcher.parseListing(LISTING);

        assertThat(items).extracting(FetchedItem::getExternalId).containsExactly("reddit:t3_good", "reddit:t3_link");

        FetchedItem self = items.get(0);
        assertThat(self.getUrl()).isEqualTo("https://www.reddit.com/r/loseit/comments/good/lost/");
        assertThat(self.getContent()).isEqualTo(LONG_TEXT.trim());
        assertThat(self.getScore()).isEqualTo(540);

        FetchedItem link = items.get(1);
        assertThat(link.getUrl()).isEqualTo("https://www.bmj.com/content/123");
        assertThat(link.getContent()).isNull();
    }

    @Test
    void normalizesSubredditAddress() {
        assertThat(RedditSourceFetcher.normalizeSubreddit("nutrition")).isEqualTo("nutrition");
        assertThat(RedditSourceFetcher.normalizeSubreddit("r/loseit")).isEqualTo("loseit");
        assertThat(RedditSourceFetcher.normalizeSubreddit("https://old.reddit.com/r/StrongerByScience/")).isEqualTo("StrongerByScience");
        assertThat(RedditSourceFetcher.normalizeSubreddit("https://www.reddit.com/r/nutrition/top/?t=week")).isEqualTo("nutrition");
        assertThat(RedditSourceFetcher.normalizeSubreddit("  ")).isNull();
    }
}
