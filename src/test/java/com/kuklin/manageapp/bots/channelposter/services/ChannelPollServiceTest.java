package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import com.kuklin.manageapp.bots.channelposter.entities.ChannelPoll;
import com.kuklin.manageapp.bots.channelposter.model.PollContent;
import com.kuklin.manageapp.bots.channelposter.repositories.ChannelPollRepository;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.polls.Poll;
import org.telegram.telegrambots.meta.api.objects.polls.PollOption;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChannelPollServiceTest {

    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");

    private final ChannelPollRepository repository = mock(ChannelPollRepository.class);
    private final ChannelPosterProperties properties = new ChannelPosterProperties();
    private final ChannelPollService service = new ChannelPollService(repository, properties);

    @Test
    void resultsAreDueNextDayAtConfiguredHour() {
        // среда 14:00 МСК → четверг 21:00 МСК; и поздно вечером — всё равно следующий день
        Instant wed14 = ZonedDateTime.of(2026, 10, 21, 14, 0, 0, 0, MSK).toInstant();
        Instant wed23 = ZonedDateTime.of(2026, 10, 21, 23, 50, 0, 0, MSK).toInstant();
        Instant thu21 = ZonedDateTime.of(2026, 10, 22, 21, 0, 0, 0, MSK).toInstant();

        assertThat(ChannelPollService.getResultsDueAt(wed14, 21, MSK)).isEqualTo(thu21);
        assertThat(ChannelPollService.getResultsDueAt(wed23, 21, MSK)).isEqualTo(thu21);
    }

    @Test
    void createKeepsAnswerOnlyForQuiz() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        PollContent content = new PollContent(" Q? ", List.of(" a ", "b"), 1, " e ");

        ChannelPoll quiz = service.create(5L, content, ChannelPoll.Type.QUIZ);
        ChannelPoll regular = service.create(6L, content, ChannelPoll.Type.REGULAR);

        assertThat(quiz.getType()).isEqualTo(ChannelPoll.Type.QUIZ);
        assertThat(quiz.getQuestion()).isEqualTo("Q?");
        assertThat(quiz.getOptions()).containsExactly("a", "b");
        assertThat(quiz.getCorrectOptionId()).isEqualTo(1);
        assertThat(quiz.getExplanation()).isEqualTo("e");
        assertThat(regular.getType()).isEqualTo(ChannelPoll.Type.REGULAR);
        assertThat(regular.getCorrectOptionId()).isNull();
        assertThat(regular.getExplanation()).isNull();
    }

    @Test
    void storesVotesOfOwnPollsOnly() {
        ChannelPoll poll = new ChannelPoll().setId(1L).setTgPollId("own");
        when(repository.findByTgPollId("own")).thenReturn(Optional.of(poll));
        when(repository.findByTgPollId("preview")).thenReturn(Optional.empty());

        service.updateVotes(tgPoll("own", 7, 3));
        service.updateVotes(tgPoll("preview", 1, 0));

        assertThat(poll.getOptionVotes()).containsExactly(7, 3);
        assertThat(poll.getTotalVoters()).isEqualTo(10);
        verify(repository, times(1)).save(any());
    }

    @Test
    void publishingSetsTelegramIdsAndResultsTime() {
        properties.setPollResultsHour(21);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChannelPoll poll = service.markPublished(new ChannelPoll(), "tg", 50);

        assertThat(poll.getTgPollId()).isEqualTo("tg");
        assertThat(poll.getTgMessageId()).isEqualTo(50);
        assertThat(poll.getResultsDueAt().atZone(MSK).getHour()).isEqualTo(21);
        assertThat(poll.getResultsDueAt()).isAfter(poll.getPublishedAt());
    }

    private static Poll tgPoll(String id, int first, int second) {
        Poll poll = new Poll();
        poll.setId(id);
        PollOption a = new PollOption();
        a.setVoterCount(first);
        PollOption b = new PollOption();
        b.setVoterCount(second);
        poll.setOptions(List.of(a, b));
        poll.setTotalVoterCount(first + second);
        return poll;
    }
}
