package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfileEntry;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UserNutritionProfileEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserNutritionProfileEntryService {
    private final UserNutritionProfileEntryRepository userNutritionProfileEntryRepository;

    public UserNutritionProfileEntry getCurrentNutritionProfileEntryByUserIdOrNull(Long userId) {
        return userNutritionProfileEntryRepository
                .findByUserIdAndValidToIsNull(userId).orElse(null);
    }

    @Transactional
    public void syncWithProfile(UserNutritionProfile profile) {
        Instant now = Instant.now();

        UserNutritionProfileEntry current =
                userNutritionProfileEntryRepository
                        .findByUserIdAndValidToIsNull(profile.getUserId())
                        .orElse(null);

        UserNutritionProfileEntry newEntry = profile.toProfileEntryWithoutValidTime();
        newEntry.setValidFrom(now);

        if (current != null && current.targetsEquals(newEntry)) {
            return; // ничего не изменилось
        }

        if (current != null) {
            current.setValidTo(now);
            userNutritionProfileEntryRepository.saveAndFlush(current);
        }

        userNutritionProfileEntryRepository.save(newEntry);
    }

    public List<UserNutritionProfileEntry> getAllByUserId(Long userId) {
        return userNutritionProfileEntryRepository.findAllByUserId(userId);
    }
}
