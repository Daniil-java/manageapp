package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.repositories.TopicCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TopicCategoryService {
    private final TopicCategoryRepository topicCategoryRepository;

    public Optional<TopicCategory> getTopicCategoryByTopicTypeOrNull(TopicCategory.TopicType topicType) {
        return topicCategoryRepository.findTopicCategoryByTopicType(topicType);
    }

    public Optional<TopicCategory> findById(Long id) {
        return id == null ? Optional.empty() : topicCategoryRepository.findById(id);
    }

    public List<TopicCategory> getActive() {
        return topicCategoryRepository.findAllByIsActiveTrue();
    }

    // id → категория: подписи рубрик в списках (/q)
    public Map<Long, TopicCategory> getAllById() {
        return topicCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(TopicCategory::getId, Function.identity()));
    }
}
