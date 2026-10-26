package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.components.RubricContentGenerator;
import com.kuklin.manageapp.bots.channelposter.entities.ChannelPoll;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.RubricTopic;
import com.kuklin.manageapp.bots.channelposter.entities.ScheduleSlot;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.model.AiGeneratedContent;
import com.kuklin.manageapp.bots.channelposter.model.PollContent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.kuklin.manageapp.bots.channelposter.telegram.handlers.ReviewPosterUpdateHandler.CHANNEL_ZONE;

/**
 * Рубрики по дням недели («Миф или правда», «Факт»): пост готовится заранее под каждый слот рубрики
 * на неделю вперёд и идёт на проверку, как статья. Свободный слот рубрики статьёй не занимается.
 * Тема поста — из банка тем (RubricTopicService), банк пополняется сам, когда тем мало.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RubricPostService {

    // на сколько дней вперёд держим посты рубрики готовыми
    static final int LOOKAHEAD_DAYS = 7;

    private final ScheduleSlotService scheduleSlotService;
    private final PostQueueService postQueueService;
    private final RubricContentGenerator generator;
    private final RubricTopicService topicService;
    private final ChannelPollService pollService;
    private final GuessCaloriesService guessCaloriesService;
    private final PostReviewService reviewService;
    private final ChannelPosterTelegramBot bot;
    private final ChannelPosterBotKeyComponent keys;

    /**
     * По одному посту на рубрику за запуск, если постов в работе меньше, чем её слотов на неделю вперёд.
     *
     * @return сколько постов создано
     */
    public int generateDue() {
        int created = 0;
        LocalDateTime now = LocalDateTime.now(CHANNEL_ZONE);
        for (TopicCategory category : topicService.getRubrics()) {
            // без ключа Pixabay угадайке негде взять фото — и банк блюд не пополняем
            if (category.getTopicType() == TopicCategory.TopicType.GUESS_CALORIES && !keys.hasPixabayKey()) continue;
            List<ScheduleSlot> slots = scheduleSlotService.getSlotByTopicCategoryIdAndIsActiveTrue(category.getId());
            long upcoming = countSlotsAhead(slots, now, LOOKAHEAD_DAYS);
            long inPipeline = postQueueService.countInPipelineByCategory(category.getId());
            if (inPipeline >= upcoming) continue;

            // сбой одной рубрики (AI, сток, Telegram) не должен останавливать остальные рубрики и статьи
            try {
                if (createPost(category) != null) created++;
            } catch (Exception e) {
                log.error("Rubric {}: post generation failed", category.getTopicType(), e);
            }
        }
        if (created > 0) {
            log.info("Rubrics: {} posts created", created);
        }
        return created;
    }

    /**
     * Пополняет банк, если тем мало, берёт следующую тему и пишет по ней пост.
     *
     * @return пост на проверке или null, если тем нет или AI не справился
     */
    public PostQueue createPost(TopicCategory category) {
        refillIfLow(category);
        Optional<RubricTopic> next = topicService.findNext(category.getId());
        if (next.isEmpty()) {
            log.warn("Rubric {}: no topics in the bank", category.getTopicType());
            return null;
        }
        RubricTopic topic = next.get();
        if (category.getTopicType() == TopicCategory.TopicType.GUESS_CALORIES) {
            return guessCaloriesService.createPost(category, topic);
        }
        if (RubricContentGenerator.isPoll(category.getTopicType())) {
            return createPollPost(category, topic);
        }
        AiGeneratedContent content = generator.generate(category.getTopicType(), topic);
        if (content == null) {
            // тема остаётся новой — попробуем в следующий запуск
            log.warn("Rubric {}: AI didn't write a post on topic #{}", category.getTopicType(), topic.getId());
            return null;
        }
        PostQueue post = postQueueService.save(new PostQueue()
                .setCategoryId(category.getId())
                .setTitle(content.getPostTitle())
                .setTextContent(content.getPostText())
                .setImageDescription(content.getImagePrompt())
                .setImageQuery(content.getImageQuery())
                .setImageQueryBroad(content.getImageQueryBroad())
                .setStatus(PostQueue.PostQueueStatus.TEXT_GENERATED));
        topicService.markUsed(topic, post.getId());
        reviewService.prepareAndSendToAdmins(post);
        return post;
    }

    // квиз или опрос: без картинки, вопрос — заголовок поста, варианты — в channel_poll
    private PostQueue createPollPost(TopicCategory category, RubricTopic topic) {
        TopicCategory.TopicType type = category.getTopicType();
        PollContent content = generator.generatePoll(type, topic);
        if (content == null) {
            log.warn("Rubric {}: AI didn't make a poll on topic #{}", type, topic.getId());
            return null;
        }
        PostQueue post = postQueueService.save(new PostQueue()
                .setCategoryId(category.getId())
                .setTitle(content.question())
                .setTextContent(content.question())
                .setStatus(PostQueue.PostQueueStatus.TEXT_GENERATED));
        pollService.create(post.getId(), content,
                type == TopicCategory.TopicType.QUIZ ? ChannelPoll.Type.QUIZ : ChannelPoll.Type.REGULAR);
        topicService.markUsed(topic, post.getId());
        reviewService.prepareAndSendToAdmins(post);
        return post;
    }

    private void refillIfLow(TopicCategory category) {
        long fresh = topicService.countNew(category.getId());
        if (fresh >= RubricTopicService.MIN_NEW_TOPICS) return;

        int added = topicService.fill(category);
        if (added > 0) {
            notifyAdmins("📚 В банк тем «" + escape(category.getName()) + "» добавлено " + added
                    + ". Пролистай и выключи лишнее: /topics " + category.getTopicType().name().toLowerCase());
        } else if (fresh == 0) {
            notifyAdmins("⚠️ Банк тем «" + escape(category.getName()) + "» пуст, а AI не дал новых. "
                    + "Добавь свои: /topics add " + category.getTopicType().name().toLowerCase() + " <i>тема</i>");
        }
    }

    // сколько раз слоты сработают с этого момента за days дней (сегодняшние — только ещё не прошедшие)
    static long countSlotsAhead(List<ScheduleSlot> slots, LocalDateTime now, int days) {
        long count = 0;
        for (int d = 0; d < days; d++) {
            LocalDate date = now.toLocalDate().plusDays(d);
            for (ScheduleSlot slot : slots) {
                if (!slot.isOn(date)) continue;
                if (d == 0 && !slot.getPostTime().isAfter(now.toLocalTime())) continue;
                count++;
            }
        }
        return count;
    }

    private void notifyAdmins(String text) {
        for (Long adminId : keys.getAdminIds()) {
            try {
                bot.sendReturnedMessage(adminId, text);
            } catch (Exception e) {
                log.error("Can't notify admin {}: {}", adminId, e.getMessage());
            }
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
