package com.kuklin.manageapp.bots.caloriebot.favorite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFavoriteDishRepository extends JpaRepository<UserFavoriteDish, Long> {
    List<UserFavoriteDish> findAllByUserIdOrderByLastUsedAtDescCreatedAtDesc(Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
    Optional<UserFavoriteDish> findByIdAndUserId(Long id, Long userId);

}
