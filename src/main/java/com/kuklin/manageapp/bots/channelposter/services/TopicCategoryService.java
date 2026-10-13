package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.repositories.TopicCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TopicCategoryService {
    private final TopicCategoryRepository topicCategoryRepository;

    public Optional<TopicCategory> getTopicCategoryByTopicTypeOrNull(TopicCategory.TopicType topicType) {
        return topicCategoryRepository.findTopicCategoryByTopicType(topicType);
    }
}
