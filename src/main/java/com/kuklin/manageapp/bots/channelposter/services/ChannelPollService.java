package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import com.kuklin.manageapp.bots.channelposter.entities.ChannelPoll;
import com.kuklin.manageapp.bots.channelposter.model.PollContent;
import com.kuklin.manageapp.bots.channelposter.repositories.ChannelPollRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.polls.Poll;
import org.telegram.telegrambots.meta.api.objects.polls.PollOption;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.kuklin.manageapp.bots.channelposter.telegram.handlers.ReviewPosterUpdateHandler.CHANNEL_ZONE;

/**
 * Опросы и квизы канала: содержимое, публикация, голоса из апдейтов Telegram.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelPollService {

    private final ChannelPollRepository repository;
    private final ChannelPosterProperties properties;

    public ChannelPoll create(Long postQueueId, PollContent content, ChannelPoll.Type type) {
        boolean quiz = type != ChannelPoll.Type.REGULAR;
        return repository.save(new ChannelPoll()
                .setPostQueueId(postQueueId)
                .setType(type)
                .setQuestion(content.question().trim())
                .setOptions(content.options().stream().map(String::trim).toList())
                .setCorrectOptionId(quiz ? content.correctOptionId() : null)
                .setExplanation(quiz ? content.explanation().trim() : null));
    }

    public Optional<ChannelPoll> findByPostQueueId(Long postQueueId) {
        return postQueueId == null ? Optional.empty() : repository.findByPostQueueId(postQueueId);
    }

    /**
     * Опрос вышел в канале: запоминаем id опроса (по нему придут голоса) и сообщения (на него ответят итоги).
     */
    public ChannelPoll markPublished(ChannelPoll poll, String tgPollId, Integer tgMessageId) {
        Instant now = Instant.now();
        return repository.save(poll
                .setTgPollId(tgPollId)
                .setTgMessageId(tgMessageId)
                .setPublishedAt(now)
                .setResultsDueAt(getResultsDueAt(now, properties.getPollResultsHour(), CHANNEL_ZONE)));
    }

    /**
     * Новое состояние опроса от Telegram. Чужие опросы (превью у админа) не храним.
     */
    public void updateVotes(Poll tgPoll) {
        repository.findByTgPollId(tgPoll.getId()).ifPresent(poll -> {
            List<Integer> votes = tgPoll.getOptions().stream()
                    .map(PollOption::getVoterCount)
                    .map(v -> v == null ? 0 : v)
                    .toList();
            int total = tgPoll.getTotalVoterCount() == null ? 0 : tgPoll.getTotalVoterCount();
            repository.save(poll.setOptionVotes(votes).setTotalVoters(total));
        });
    }

    public List<ChannelPoll> getDueForResults() {
        return repository.findAllByResultsSentAtIsNullAndResultsDueAtBefore(Instant.now());
    }

    /**
     * @param resultsPostId пост с итогами; null — итоги пропущены
     */
    public ChannelPoll markResultsSent(ChannelPoll poll, Long resultsPostId) {
        return repository.save(poll.setResultsSentAt(Instant.now()).setResultsPostId(resultsPostId));
    }

    // итоги — на следующий день в hour (время канала)
    static Instant getResultsDueAt(Instant publishedAt, int hour, ZoneId zone) {
        return publishedAt.atZone(zone).toLocalDate().plusDays(1)
                .atTime(LocalTime.of(hour, 0))
                .atZone(zone)
                .toInstant();
    }
}
