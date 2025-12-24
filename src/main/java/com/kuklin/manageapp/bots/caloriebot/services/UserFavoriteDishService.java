package com.kuklin.manageapp.bots.caloriebot.services;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserFavoriteDish;
import com.kuklin.manageapp.bots.caloriebot.repository.UserFavoriteDishRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserFavoriteDishService {
    private final UserFavoriteDishRepository userFavoriteDishRepository;
    private final DishService dishService;

    public List<UserFavoriteDish> getAllForUser(Long userId) {
        return userFavoriteDishRepository.findAllByUserIdOrderByLastUsedAtDescCreatedAtDesc(userId);
    }

    /**
     * Сохранить блюдо как избранное по уже существующему Dish.
     */
    public UserFavoriteDish saveFromDish(Long userId, Long dishId) {
        Dish dish = dishService.getDishByIdOrNull(dishId);

        if (dish == null || !dish.getUserId().equals(userId)) {
            return null;
        }

        // Не даём одинаковые названия у одного пользователя
        if (userFavoriteDishRepository.existsByUserIdAndNameIgnoreCase(userId, dish.getName())) {
            // Можно вместо null кидать своё исключение
            return null;
        }

        return userFavoriteDishRepository.save(UserFavoriteDish.fromDish(dish));
    }

    /**
     * Добавить в дневник блюдо из избранного.
     */
    public Dish addDishFromFavorite(Long userId, Long favoriteId) {
        UserFavoriteDish favorite = userFavoriteDishRepository.findByIdAndUserId(favoriteId, userId)
                .orElse(null);
        if (favorite == null) {
            return null;
        }

        Dish saved = dishService.addDishOrNull(favorite);

        favorite.setLastUsedAt(Instant.now());
        userFavoriteDishRepository.save(favorite);

        return saved;
    }

    public void deleteFavorite(Long favoriteId, Long userId) {
        userFavoriteDishRepository.findByIdAndUserId(favoriteId, userId)
                .ifPresent(userFavoriteDishRepository::delete);
    }
}
