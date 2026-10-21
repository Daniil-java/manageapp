package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.DishDto;
import com.kuklin.manageapp.bots.channelposter.components.source.PixabayClient;
import com.kuklin.manageapp.bots.channelposter.entities.ChannelPoll;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.RubricTopic;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.model.PollContent;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * «Угадай калорийность»: фото блюда с Pixabay, калории считает распознавание калорийного бота (тот же промпт,
 * что у пользователей), квиз из четырёх вариантов. Итоги — PollResultsService, как у квизов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GuessCaloriesService {

    // ниже — на фото скорее напиток без калорий или ИИ ошибся: такой квиз неинтересен
    static final int MIN_KCAL = 50;
    // фото выбираем из первых — дальше поиск уходит от темы
    private static final int TOP_PHOTOS = 5;
    // варианты ответа: правильный — на месте 1.0; где он окажется среди вариантов, решает случай
    private static final double[][] PATTERNS = {
            {1.0, 1.35, 1.75, 2.3},
            {0.7, 1.0, 1.4, 1.9},
            {0.5, 0.7, 1.0, 1.45},
            {0.4, 0.55, 0.75, 1.0}
    };

    private final PixabayClient stock;
    private final DishService dishService;
    private final PostQueueService postQueueService;
    private final ChannelPollService pollService;
    private final PostReviewService reviewService;
    private final RubricTopicService topicService;
    private final Random random = new Random();

    /**
     * @return пост на проверке или null (нет фото, распознавание не справилось — тема остаётся новой)
     */
    public PostQueue createPost(TopicCategory category, RubricTopic topic) {
        String dish = topic.getText();
        List<PixabayClient.StockPhoto> photos;
        try {
            photos = stock.search(dish);
        } catch (Exception e) {
            log.warn("Guess calories: Pixabay search failed for «{}»: {}", dish, e.getMessage());
            return null;
        }
        if (photos.isEmpty()) {
            // по этому блюду фото нет и не будет — тему выключаем, чтобы не искать её каждый запуск
            log.info("Guess calories: no Pixabay photos for «{}», topic #{} disabled", dish, topic.getId());
            topicService.setEnabled(topic.getId(), false);
            return null;
        }
        PixabayClient.StockPhoto photo = photos.get(random.nextInt(Math.min(TOP_PHOTOS, photos.size())));

        List<DishDto> dishes = dishService.recognizePhotoForChannelOrNull(photo.imageUrl(), dish);
        Totals totals = Totals.of(dishes);
        if (totals == null || totals.kcal() < MIN_KCAL) {
            log.warn("Guess calories: recognition gave nothing usable for «{}» ({})", dish, photo.pageUrl());
            return null;
        }

        PostQueue post = postQueueService.save(new PostQueue()
                .setCategoryId(category.getId())
                .setTitle("Угадай калорийность: " + dish)
                .setTextContent(buildCaption(dish, photo))
                .setStatus(PostQueue.PostQueueStatus.TEXT_GENERATED));
        pollService.create(post.getId(), buildQuiz(dish, totals, random), ChannelPoll.Type.CALORIES);
        if (!reviewService.attachImageFromUrl(post, photo.imageUrl())) {
            // без фото угадайка не имеет смысла — убираем пост, тема остаётся новой
            log.warn("Guess calories: Telegram didn't take photo {}", photo.imageUrl());
            postQueueService.removePost(post.getId());
            return null;
        }
        topicService.markUsed(topic, post.getId());
        reviewService.prepareAndSendToAdmins(post);
        return post;
    }

    /** Сумма по блюдам на фото. */
    record Totals(int kcal, int proteins, int fats, int carbs, int weight) {
        static Totals of(List<DishDto> dishes) {
            if (dishes == null || dishes.isEmpty()) return null;
            int kcal = 0, p = 0, f = 0, c = 0, w = 0;
            for (DishDto d : dishes) {
                kcal += value(d.getCalories());
                p += value(d.getProteins());
                f += value(d.getFats());
                c += value(d.getCarbohydrates());
                w += value(d.getWeight());
            }
            return new Totals(kcal, p, f, c, w);
        }

        private static int value(Integer v) {
            return v == null ? 0 : v;
        }
    }

    // подпись к фото: само блюдо, что делать, автор фото (просят правила Pixabay)
    static String buildCaption(String dish, PixabayClient.StockPhoto photo) {
        StringBuilder sb = new StringBuilder("<b>🍽 Угадай калорийность</b>\n")
                .append("«").append(escape(dish)).append("» — сколько калорий в этой порции? ")
                .append("Ответ — в квизе ниже, итоги завтра вечером.");
        if (photo.author() != null && photo.pageUrl() != null) {
            sb.append("\n\n<i>Фото: <a href=\"").append(escape(photo.pageUrl()).replace("\"", "&quot;")).append("\">")
                    .append(escape(photo.author())).append("</a> / Pixabay</i>");
        }
        return sb.toString();
    }

    /**
     * Четыре варианта по возрастанию, правильный — оценка бота. Где он окажется (самый малый … самый большой),
     * решает случай, чтобы ответ нельзя было угадать по положению.
     */
    static PollContent buildQuiz(String dish, Totals totals, Random random) {
        int kcal = roundTo10(totals.kcal());
        double[] pattern = PATTERNS[random.nextInt(PATTERNS.length)];
        List<String> options = new ArrayList<>();
        int correct = 0;
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i] == 1.0) correct = i;
            options.add("≈ " + roundTo10((int) Math.round(totals.kcal() * pattern[i])) + " ккал");
        }
        String explanation = "Бот " + BotIdentifier.CALORIE_BOT.getBotUsername() + " насчитал ≈" + kcal + " ккал: Б "
                + totals.proteins() + " · Ж " + totals.fats() + " · У " + totals.carbs() + " г"
                + (totals.weight() > 0 ? ", порция ≈" + totals.weight() + " г" : "");
        String question = "Сколько калорий в порции на фото выше? («" + dish + "»)";
        return new PollContent(
                question.length() > PollContent.MAX_QUESTION ? "Сколько калорий в порции на фото выше?" : question,
                options, correct,
                explanation.length() > PollContent.MAX_EXPLANATION
                        ? explanation.substring(0, PollContent.MAX_EXPLANATION) : explanation);
    }

    static int roundTo10(int value) {
        return (int) Math.round(value / 10.0) * 10;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
