package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.components.ArticleContentGenerator;
import com.kuklin.manageapp.bots.channelposter.entities.PostImage;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.ScheduleSlot;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.model.AiGeneratedContent;
import com.kuklin.manageapp.bots.channelposter.model.PostQueueNotFoundException;
import com.kuklin.manageapp.bots.channelposter.model.TopicCategoryNotFoundException;
import com.kuklin.manageapp.bots.channelposter.repositories.PostQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.*;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostQueueService {
    private final PostQueueRepository postQueueRepository;
    private final TopicCategoryService topicCategoryService;
    private final ArticleContentGenerator articleContentGenerator;
    private final PostImageService postImageService;
    private final ScheduleSlotService scheduleSlotService;

    // посты, готовые к публикации
    public List<PostQueue> getPostsReadyToPublish() {
        return postQueueRepository.findAllByStatusAndScheduledAtBefore(
                PostQueue.PostQueueStatus.QUEUED,
                Instant.now()
        );
    }

    // Маркировка как "Отправлено"
    public void markAsSentAndDeleteFile(Long id, Integer tgMessageId) {
        postQueueRepository.findById(id).ifPresent(post -> {
            post.setStatus(PostQueue.PostQueueStatus.SENT);
            post.setSentAt(Instant.now());
            post.setTgMessageId(tgMessageId);
            postQueueRepository.save(post);
        });

        postImageService.deletePostImageByPostQueueId(id);
    }

    // Маркировка ошибки
    public void markAsFailed(Long id) {
        postQueueRepository.findById(id).ifPresent(post -> {
            post.setStatus(PostQueue.PostQueueStatus.FAILED);
            postQueueRepository.save(post);
        });
    }

    // генерация текста поста через AI и создание записи
    public PostQueue createPostQueueByText(String article, TopicCategory.TopicType topicType) throws TopicCategoryNotFoundException {
        TopicCategory topicCategory = topicCategoryService.getTopicCategoryByTopicTypeOrNull(topicType)
                .orElseThrow(() -> new TopicCategoryNotFoundException());

        //пупупу
        AiGeneratedContent content = articleContentGenerator.generateText(article);
        if (content == null) return null;

        return postQueueRepository.save(new PostQueue()
                .setTextContent(content.getPostText())
                .setCategoryId(topicCategory.getId())
                .setStatus(PostQueue.PostQueueStatus.TEXT_GENERATED)
                .setImageDescription(content.getImagePrompt()))
                ;
    }

    // генерация изображения для поста
    public PostImage generateImage(Long postId) throws PostQueueNotFoundException, IOException {
        PostQueue postQueue = getPostQueueById(postId);
        return articleContentGenerator.generateImage(postQueue);
    }

    public void removePost(Long postId) {
        postImageService.deletePostImageByPostQueueId(postId);
        postQueueRepository.deleteById(postId);
    }

    public PostQueue getPostQueueById(Long id) throws PostQueueNotFoundException {
        return postQueueRepository.findById(id)
                .orElseThrow(() -> new PostQueueNotFoundException());
    }

    public PostQueue setStatusImageCreated(Long postId) throws PostQueueNotFoundException {
        PostQueue postQueue = getPostQueueById(postId);
        postImageService.setStatus(postId, PostImage.ImageStatus.APPROVED);
        postQueue.setStatus(PostQueue.PostQueueStatus.IMAGE_GENERATED);
        return postQueueRepository.save(postQueue);
    }

    public PostQueue save(PostQueue setTgMessageId) {
        return postQueueRepository.save(setTgMessageId);
    }

    public List<PostQueue> getPlannedAndSentFor(Instant startAt, Instant endAt) {
        List<PostQueue.PostQueueStatus> targetStatuses = List.of(
                PostQueue.PostQueueStatus.SENT,
                PostQueue.PostQueueStatus.QUEUED
        );

        return postQueueRepository.findPlannedAndSentFor(
                startAt,
                endAt,
                targetStatuses
        );
    }

    public List<PostQueue> getPostsByStatus(PostQueue.PostQueueStatus status) {
        return postQueueRepository.findAllByStatus(status);
    }

    // Метод для назначения времени посту при аппруве
    public PostQueue assignNextAvailableSlot(Long postId, ZoneId zoneId) throws PostQueueNotFoundException {
        return assignNextAvailableSlot(getPostQueueById(postId), zoneId);
    }
    public PostQueue assignNextAvailableSlot(PostQueue postQueue, ZoneId zoneId) {
        if (postQueue.getScheduledAt() != null) {
            return postQueue;
        }
        // 1. Получаем все активные слоты для категории, сортируем их по времени
        List<ScheduleSlot> categorySlots = scheduleSlotService.getSlotByTopicCategoryIdAndIsActiveTrue(postQueue.getCategoryId());
        categorySlots.sort(Comparator.comparing(ScheduleSlot::getPostTime));

        LocalDate dateToCheck = LocalDate.now(zoneId);
        Instant scheduledTime = null;
        int daysOffset = 0;

        // Ищем свободный слот в течение ближайших 7 дней (чтобы не зациклиться)
        while (scheduledTime == null && daysOffset < 7) {
            Instant startOfDay = dateToCheck.atStartOfDay(zoneId).toInstant();
            Instant endOfDay = dateToCheck.atTime(LocalTime.MAX).atZone(zoneId).toInstant();

            List<PostQueue> plannedPosts = getPlannedAndSentFor(startOfDay, endOfDay);

            for (ScheduleSlot slot : categorySlots) {
                LocalDateTime slotDateTime = LocalDateTime.of(dateToCheck, slot.getPostTime());

                // Если проверяем "сегодня" и время слота уже прошло — пропускаем этот слот
                if (daysOffset == 0 && slotDateTime.isBefore(LocalDateTime.now(zoneId))) {
                    continue;
                }

                // Проверяем, не занят ли этот слот
                boolean isSlotTaken = plannedPosts.stream()
                        .anyMatch(p -> isSameTime(p.getScheduledAt(), slot.getPostTime(), zoneId));

                if (!isSlotTaken) {
                    scheduledTime = slotDateTime.atZone(zoneId).toInstant();
                    break; // Нашли ближайший слот, выходим из цикла слотов
                }
            }

            // Если на эту дату ничего не нашли, переходим к следующему дню
            if (scheduledTime == null) {
                dateToCheck = dateToCheck.plusDays(1);
                daysOffset++;
            }
        }

        if (scheduledTime == null) {
            return null;
        }

        postQueue.setScheduledAt(scheduledTime);
        postQueue.setStatus(PostQueue.PostQueueStatus.QUEUED);
        return postQueueRepository.save(postQueue);
    }

    private boolean isSameTime(Instant instant, LocalTime time, ZoneId zoneId) {
        if (instant == null) return false;
        LocalTime instantTime = instant.atZone(zoneId).toLocalTime();
        return instantTime.getHour() == time.getHour() && instantTime.getMinute() == time.getMinute();
    }
}
