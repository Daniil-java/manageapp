package com.kuklin.manageapp.bots.channelposter.components.source;

import com.kuklin.manageapp.bots.channelposter.entities.source.ContentSource;
import com.kuklin.manageapp.bots.channelposter.model.source.FetchedItem;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * RSS 2.0 и Atom: ScienceDaily, Medical Xpress, The Conversation, блоги на WordPress, N+1 и т.п.
 */
@Component
@RequiredArgsConstructor
public class RssSourceFetcher implements SourceFetcher {

    private final SourceHttpClient http;

    @Override
    public List<FetchedItem> fetch(ContentSource source) throws Exception {
        String xml = http.get(source.getAddress());
        List<FetchedItem> items = parseFeed(xml, source.getMaxItems());
        if (items.isEmpty() && !looksLikeFeed(xml)) {
            throw new IllegalStateException("ответ не похож на RSS/Atom");
        }
        return items;
    }

    @Override
    public ContentSource.SourceType supportedType() {
        return ContentSource.SourceType.RSS;
    }

    static boolean looksLikeFeed(String xml) {
        return xml != null && (xml.contains("<rss") || xml.contains("<feed") || xml.contains("<rdf:RDF"));
    }

    /**
     * Разбирает ленту и возвращает до maxItems самых свежих материалов.
     */
    public static List<FetchedItem> parseFeed(String xml, Integer maxItems) {
        List<FetchedItem> result = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            return result;
        }
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());

        for (Element el : doc.select("item, entry")) {
            FetchedItem item = el.tagName().equals("entry") ? parseAtomEntry(el) : parseRssItem(el);
            if (item != null) {
                result.add(item);
            }
        }

        result.sort(Comparator.comparing(FetchedItem::getPublishedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        int limit = maxItems == null || maxItems <= 0 ? result.size() : maxItems;
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private static FetchedItem parseRssItem(Element el) {
        String title = childText(el, "title");
        String link = childText(el, "link");
        String guid = childText(el, "guid");
        if (TextUtils.isBlank(link) && guid != null && guid.startsWith("http")) {
            link = guid;
        }
        String summary = TextUtils.htmlToText(childText(el, "description"));
        String content = TextUtils.htmlToText(childText(el, "content|encoded"));
        Instant published = TextUtils.parseFeedDate(firstNonBlank(
                childText(el, "pubDate"), childText(el, "dc|date")));
        return build(firstNonBlank(link, guid), link, title, summary, content, published);
    }

    private static FetchedItem parseAtomEntry(Element el) {
        String title = childText(el, "title");
        String link = null;
        for (Element l : el.children()) {
            if (!l.tagName().equals("link")) continue;
            String rel = l.attr("rel");
            if (rel.isEmpty() || rel.equals("alternate")) {
                link = l.attr("href");
                break;
            }
        }
        String id = childText(el, "id");
        String summary = TextUtils.htmlToText(childText(el, "summary"));
        String content = TextUtils.htmlToText(childText(el, "content"));
        Instant published = TextUtils.parseFeedDate(firstNonBlank(
                childText(el, "published"), childText(el, "updated")));
        return build(firstNonBlank(id, link), link, title, summary, content, published);
    }

    private static FetchedItem build(String externalId, String link, String title,
                                     String summary, String content, Instant published) {
        if (TextUtils.isBlank(externalId) || TextUtils.isBlank(title)) {
            return null;
        }
        // часто content:encoded = тот же анонс; держим content только если он заметно длиннее
        if (content != null && summary != null && content.length() <= summary.length() + 50) {
            content = null;
        }
        if (summary == null && content != null) {
            summary = TextUtils.truncate(content, 600);
        }
        return new FetchedItem()
                .setExternalId(TextUtils.truncate(externalId.trim(), 1000))
                .setUrl(link == null ? null : link.trim())
                .setTitle(Parser.unescapeEntities(title.trim(), false))
                .setSummary(summary)
                .setContent(content)
                .setPublishedAt(published);
    }

    // текст прямого потомка: вложенные теги с тем же именем (например, <title> у <image>) не трогаем
    private static String childText(Element el, String tag) {
        String name = tag.replace('|', ':');
        for (Element child : el.children()) {
            if (child.tagName().equalsIgnoreCase(name)) {
                // CDATA и экранированный HTML — оба приходят как текст узла
                String text = child.wholeText();
                return text == null || text.isBlank() ? null : text.trim();
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!TextUtils.isBlank(v)) return v;
        }
        return null;
    }
}
