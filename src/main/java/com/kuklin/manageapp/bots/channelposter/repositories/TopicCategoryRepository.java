package com.kuklin.manageapp.bots.channelposter.repositories;

import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicCategoryRepository extends JpaRepository<TopicCategory, Long> {
    List<TopicCategory> findAllByIsActiveTrue();

    Optional<TopicCategory> findTopicCategoryByTopicType(TopicCategory.TopicType topicType);
}
