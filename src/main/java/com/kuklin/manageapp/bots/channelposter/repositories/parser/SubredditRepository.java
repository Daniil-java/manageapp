package com.kuklin.manageapp.bots.channelposter.repositories.parser;

import com.kuklin.manageapp.bots.channelposter.entities.parser.Subreddit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @deprecated Репозиторий subreddit.
 * <p>Устарело в et-85. Причина: old.reddit.com без логина теперь редиректит на /login (reason=lor2) —
 * парсер получал страницу входа вместо ленты и находил 0 постов, а ошибки уходили только в warn-лог.
 * Поэтому в канал почти ничего не приходило. Вместо Reddit-only конвейера сделан общий:
 * ContentSource / SourceItem, парсеры RSS, PubMed и Reddit (официальный API) и ContentPipeline.
 * <p>Код не запускается по расписанию и не пополняет таблицы subreddit / reddit_post.
 * Оставлен для истории; удалить вместе с таблицами отдельной миграцией.
 */
@Deprecated(since = "et-85", forRemoval = true)
@Repository
public interface SubredditRepository extends JpaRepository<Subreddit, Long> {

    List<Subreddit> findAllByActiveTrue();
}
