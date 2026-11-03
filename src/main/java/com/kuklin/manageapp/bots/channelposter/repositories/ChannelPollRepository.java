package com.kuklin.manageapp.bots.channelposter.repositories;

import com.kuklin.manageapp.bots.channelposter.entities.ChannelPoll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelPollRepository extends JpaRepository<ChannelPoll, Long> {

    Optional<ChannelPoll> findByPostQueueId(Long postQueueId);

    Optional<ChannelPoll> findByTgPollId(String tgPollId);

    List<ChannelPoll> findAllByResultsSentAtIsNullAndResultsDueAtBefore(Instant now);
}
