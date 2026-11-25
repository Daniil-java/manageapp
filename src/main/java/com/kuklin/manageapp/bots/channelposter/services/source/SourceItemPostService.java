package com.kuklin.manageapp.bots.channelposter.services.source;

import com.kuklin.manageapp.bots.channelposter.components.source.ArticleTextExtractor;
import com.kuklin.manageapp.bots.channelposter.components.source.TextUtils;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.entities.source.ContentSource;
import com.kuklin.manageapp.bots.channelposter.entities.source.SourceItem;
import com.kuklin.manageapp.bots.channelposter.services.PostQueueService;
import com.kuklin.manageapp.bots.channelposter.services.PostReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Одобренный материал → полный текст → пост (текст + картинка) → превью админам.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SourceItemPostService {

    // столько текста уже достаточно, страницу не скачиваем (PubMed-абстракт, длинный пост Reddit, content:encoded)
    private static final int ENOUGH_CONTENT_CHARS = 1200;
    // меньше — пересказывать нечего (анонсы Medical Xpress — 350–700 символов, сами статьи закрыты для ботов)
    private static final int MIN_TEXT_CHARS = 300;

    private final ArticleTextExtractor extractor;
    private final PostQueueService postQueueService;
    private final PostReviewService reviewService;
    private final SourceItemService itemService;
    private final ContentSourceService sourceService;

    /**
     * @return пост на проверке или null, если материал не подошёл
     */
    public PostQueue createPost(SourceItem item) {
        ContentSource source = sourceService.findById(item.getSourceId()).orElse(null);
        String sourceName = source == null ? null : source.getName();

        String text = resolveText(item, source);
        if (text == null) {
            fail(item, "не удалось получить текст");
            return null;
        }

        String article = "Источник: " + (sourceName == null ? "-" : sourceName) + "\n"
                + "Заголовок: " + item.getTitle() + "\n\n"
                + text;

        PostQueue post;
        try {
            post = postQueueService.createPostQueueByText(article, TopicCategory.TopicType.ARTICLE);
        } catch (Exception e) {
            log.error("Source item #{}: post generation failed", item.getId(), e);
            post = null;
        }
        if (post == null) {
            fail(item, "AI не сгенерировал пост");
            return null;
        }

        post = postQueueService.save(post
                .setSourceItemId(item.getId())
                .setSourceUrl(item.getUrl())
                .setSourceName(shortName(sourceName))
                .setAiScore(item.getAiScore()));
        itemService.save(item.setStatus(SourceItem.Status.PROCESSED).setPostQueueId(post.getId()));

        reviewService.prepareAndSendToAdmins(post);
        return post;
    }

    private String resolveText(SourceItem item, ContentSource source) {
        String content = item.getContent();
        if (content != null && content.length() >= ENOUGH_CONTENT_CHARS) {
            return content;
        }
        boolean isPubMed = source != null && source.getType() == ContentSource.SourceType.PUBMED;
        if (!isPubMed && item.getUrl() != null && !item.getUrl().contains("reddit.com/r/")) {
            String extracted = extractor.extract(item.getUrl());
            if (extracted != null) {
                return extracted;
            }
        }
        // страница закрыта (403, Cloudflare) — работаем с тем, что было в ленте
        String fallback = longest(content, item.getSummary());
        return fallback != null && fallback.length() >= MIN_TEXT_CHARS ? fallback : null;
    }

    private static String longest(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.length() >= b.length() ? a : b;
    }

    // «ScienceDaily · Nutrition» → «ScienceDaily»: в подписи поста рубрика не нужна
    static String shortName(String sourceName) {
        if (sourceName == null) return null;
        int dot = sourceName.indexOf(" · ");
        return dot > 0 ? sourceName.substring(0, dot) : sourceName;
    }

    private void fail(SourceItem item, String reason) {
        log.warn("Source item #{} failed: {}", item.getId(), reason);
        itemService.save(item.setStatus(SourceItem.Status.FAILED).setAiReason(TextUtils.truncate(reason, 500)));
    }
}
