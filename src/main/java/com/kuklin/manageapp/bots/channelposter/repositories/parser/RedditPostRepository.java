package com.kuklin.manageapp.bots.channelposter.repositories.parser;

import com.kuklin.manageapp.bots.channelposter.entities.parser.RedditPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface RedditPostRepository extends JpaRepository<RedditPost, Long> {

    Optional<RedditPost> findByRedditId(String redditId);

    boolean existsByRedditId(String redditId);

    List<RedditPost> findTop50ByStatusOrderByParsedAtAsc(RedditPost.PostStatus status);

    List<RedditPost> findAllByRedditIdIn(List<String> redditIds);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
                UPDATE RedditPost p
                SET p.status = :status
                WHERE p.id IN :ids
            """)
    void updateStatusByIds(
            @Param("ids") List<Long> ids,
            @Param("status") RedditPost.PostStatus status
    );
}
