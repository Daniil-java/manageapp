package com.kuklin.manageapp.bots.channelposter.model;

import com.kuklin.manageapp.bots.channelposter.entities.parser.RedditPost;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

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
