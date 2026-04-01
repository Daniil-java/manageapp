package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.entities.PostImage;
import com.kuklin.manageapp.bots.channelposter.repositories.PostImageRepository;
import com.kuklin.manageapp.common.library.utils.FilesUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostImageService {
    private final PostImageRepository postImageRepository;
    public static final String IMG_DIR = "poster/article";

    public PostImage saveNewImage(
            PostImage.ImageSource imageSource, PostImage.ImageStatus status,
            String filepath, Long postQueueId
    ) {
        return postImageRepository.save(new PostImage()
                .setSource(imageSource)
                .setStatus(status)
                .setFilePath(filepath)
                .setPostQueueId(postQueueId)
        );
    }

    public PostImage getByPostQueueIdOrNull(Long postQueueId) {
        return postImageRepository.findByPostQueueId(postQueueId).orElse(null);
    }
    public void deletePostImageByPostQueueId(Long postQueueId) {
        PostImage postImage = getByPostQueueIdOrNull(postQueueId);
        if (postImage == null) {
            log.error("PostImage not found! Check dir!");
            return;
        }
        if (postImage.getFilePath() != null) {
            FilesUtils.deleteImage(postImage.getFilePath());
        }
        postImageRepository.deleteById(postImage.getId());
    }

    public PostImage setStatus(Long postId, PostImage.ImageStatus approved) {
        return postImageRepository.save(getByPostQueueIdOrNull(postId).setStatus(approved));
    }
}
