package com.kuklin.manageapp.bots.channelposter.model;

import com.kuklin.manageapp.bots.channelposter.entities.parser.RedditPost;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * @deprecated DTO старого парсера. Заменён на FetchedItem.
 * <p>Устарело в et-85. Причина: old.reddit.com без логина теперь редиректит на /login (reason=lor2) —
 * парсер получал страницу входа вместо ленты и находил 0 постов, а ошибки уходили только в warn-лог.
 * Поэтому в канал почти ничего не приходило. Вместо Reddit-only конвейера сделан общий:
 * ContentSource / SourceItem, парсеры RSS, PubMed и Reddit (официальный API) и ContentPipeline.
 * <p>Код не запускается по расписанию и не пополняет таблицы subreddit / reddit_post.
 * Оставлен для истории; удалить вместе с таблицами отдельной миграцией.
 */
@Deprecated(since = "et-85", forRemoval = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class RedditPostDto {

    private String redditId;
    private Long subredditId;

    private String url;
    private String title;
    private String content;

    private RedditPost.ContentType contentType;

    private String author;
    private Integer score;
    private Integer commentsCount;

    private Instant postCreated;

    /**
     * Конвертация в сущность
     */
    public RedditPost toEntity() {
        return new RedditPost()
                .setRedditId(redditId)
                .setSubredditId(subredditId)
                .setUrl(url)
                .setTitle(title)
                .setContent(content)
                .setContentType(contentType)
                .setAuthor(author)
                .setScore(score)
                .setCommentsCount(commentsCount)
                .setPostCreated(postCreated);
        // status и parsedAt проставятся сами
    }
}
