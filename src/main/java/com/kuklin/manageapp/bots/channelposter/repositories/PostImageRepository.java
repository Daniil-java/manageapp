package com.kuklin.manageapp.bots.channelposter.repositories;

import com.kuklin.manageapp.bots.channelposter.entities.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    Optional<PostImage> findByPostQueueId(Long postQueueId);
    Optional<PostImage> findByFilePath(String filepath);
}
