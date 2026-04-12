package com.kuklin.manageapp.bots.nicotinebot.components;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SmokingRecordService {

    private final SmokingRecordRepository repository;

    public SmokingRecord create(Long userId) {
        SmokingRecord event = new SmokingRecord()
                .setUserId(userId)
                .setSmokedAt(Instant.now())
                ;

        return repository.save(event);
    }

    public List<SmokingRecord> getAllByUser(Long userId) {
        return repository.findByUserId(userId);
    }

    public List<SmokingRecord> getByPeriod(Long userId, Instant from, Instant to) {
        return repository.findByUserIdAndSmokedAtBetween(userId, from, to);
    }

    public Optional<SmokingRecord> getLastSmokingEvent(Long userId) {
        return repository.findTopByUserIdOrderBySmokedAtDesc(userId);
    }

}
