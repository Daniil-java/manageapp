package com.kuklin.manageapp.bots.channelposter.services.parser;

import com.kuklin.manageapp.bots.channelposter.entities.parser.Subreddit;
import com.kuklin.manageapp.bots.channelposter.repositories.parser.SubredditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubredditService {

    private final SubredditRepository subredditRepository;

    //TODO Сделать кастомные ошибк
    @Transactional
    public Subreddit addOrNull(String url) {

        String normalizedUrl = normalizeUrlOrNull(url);
        String name = extractSubredditNameOrNull(normalizedUrl);

        return subredditRepository.save(
                new Subreddit()
                        .setName(name)
                        .setUrl(normalizedUrl)
                        .setActive(true)
        );
    }

    private String normalizeUrlOrNull(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        url = url.trim();

        // если пришёл обычный reddit → переводим в old
        url = url.replace("https://www.reddit.com", "https://old.reddit.com")
                .replace("http://www.reddit.com", "https://old.reddit.com")
                .replace("https://reddit.com", "https://old.reddit.com");

        // проверка домена
        if (!url.startsWith("https://old.reddit.com/r/")) {
            log.warn("Not old reddit subreddit URL!");
            return null;
        }

        // добавляем слеш в конце
        if (!url.endsWith("/")) {
            url = url + "/";
        }

        return url;
    }

    private String extractSubredditNameOrNull(String url) {
        // https://old.reddit.com/r/java/

        String[] parts = url.split("/");

        for (int i = 0; i < parts.length; i++) {
            if ("r".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }

        return null;
    }

    public List<Subreddit> getAllActive() {
        return subredditRepository.findAllByActiveTrue();
    }

    public void delete(Long id) {
        subredditRepository.deleteById(id);
    }

    public void toggle(Long id) {
        Subreddit sub = subredditRepository.findById(id)
                .orElseThrow();

        sub.setActive(!Boolean.TRUE.equals(sub.getActive()));
        subredditRepository.save(sub);
    }
}
