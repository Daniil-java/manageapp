package com.kuklin.manageapp.bots.channelposter.services.parser;

import com.kuklin.manageapp.bots.channelposter.entities.parser.RedditPost;
import com.kuklin.manageapp.bots.channelposter.model.RedditPostDto;
import com.kuklin.manageapp.bots.channelposter.repositories.parser.RedditPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedditPostService {

    private final RedditPostRepository postRepository;
    /**
     * Сохраняет пост, если его ещё нет (дедупликация)
     */
    @Transactional
    public void saveIfNotExists(RedditPost post) {
        if (postRepository.existsByRedditId(post.getRedditId())) {
            return;
        }

        post.setStatus(RedditPost.PostStatus.NEW);
        postRepository.save(post);
    }

    public List<RedditPost> getByStatus(RedditPost.PostStatus status) {
        return postRepository.findTop50ByStatusOrderByParsedAtAsc(status);
    }

    @Transactional
    public void markApproved(Long id) {
        updateStatus(id, RedditPost.PostStatus.APPROVED);
    }

    @Transactional
    public void markRejected(Long id) {
        updateStatus(id, RedditPost.PostStatus.REJECTED);
    }

    @Transactional
    public void markProcessed(Long id) {
        updateStatus(id, RedditPost.PostStatus.PROCESSED);
    }

    @Transactional
    public void markFailed(Long id) {
        updateStatus(id, RedditPost.PostStatus.FAILED);
    }

    private void updateStatus(Long id, RedditPost.PostStatus status) {
        RedditPost post = postRepository.findById(id)
                .orElseThrow();

        post.setStatus(status);
        postRepository.save(post);
    }

    @Transactional
    public void saveIfNotExistsAll(List<RedditPostDto> dtos) {

        if (dtos == null || dtos.isEmpty()) return;

        // 1. собираем все redditId
        List<String> ids = dtos.stream()
                .map(RedditPostDto::getRedditId)
                .toList();

        // 2. находим уже существующие
        Set<String> existingIds = postRepository.findAllByRedditIdIn(ids)
                .stream()
                .map(RedditPost::getRedditId)
                .collect(java.util.stream.Collectors.toSet());

        // 3. фильтруем новые
        List<RedditPost> toSave = dtos.stream()
                .filter(dto -> !existingIds.contains(dto.getRedditId()))
                .map(dto -> {
                    RedditPost post = dto.toEntity();
                    post.setStatus(RedditPost.PostStatus.NEW);
                    return post;
                })
                .toList();

        if (toSave.isEmpty()) return;

        // 4. сохраняем пачкой
        try {
            postRepository.saveAll(toSave);
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public void updateStatuses(List<Long> ids, RedditPost.PostStatus status) {

        if (ids == null || ids.isEmpty() || status == null) return;

        postRepository.updateStatusByIds(ids, status);
    }

    public void saveAll(List<RedditPost> list) {
        postRepository.saveAll(list);
    }
}
