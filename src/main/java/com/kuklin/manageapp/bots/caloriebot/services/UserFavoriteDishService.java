package com.kuklin.manageapp.bots.caloriebot.services;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserFavoriteDish;
import com.kuklin.manageapp.bots.caloriebot.featurerestrictions.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.featurerestrictions.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.featurerestrictions.RequiresFeature;
import com.kuklin.manageapp.bots.caloriebot.featurerestrictions.UserFeatureUsageService;
import com.kuklin.manageapp.bots.caloriebot.repository.UserFavoriteDishRepository;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserFavoriteDishService {
    private final UserFavoriteDishRepository userFavoriteDishRepository;
    private final DishService dishService;
    private final UserFeatureUsageService userFeatureUsageService;

    public List<UserFavoriteDish> getAllForUser(Long userId) {
        return userFavoriteDishRepository.findAllByUserIdOrderByLastUsedAtDescCreatedAtDesc(userId);
    }

    /**
     * Сохранить блюдо как избранное по уже существующему Dish.
     */
    @Transactional
    @RequiresFeature(value = BotFeature.DISH_FAVORITE_LIST, botIdentifier = BotIdentifier.CALORIE_BOT)
    public AccessResult<UserFavoriteDish> saveFromDish(Long userId, Long dishId) {
        Dish dish = dishService.getDishByIdOrNull(dishId);

        if (dish == null || !dish.getUserId().equals(userId)) {
            return AccessResult.success(null);
        }

        // Не даём одинаковые названия у одного пользователя
        if (userFavoriteDishRepository.existsByUserIdAndNameIgnoreCase(userId, dish.getName())) {
            // Можно вместо null кидать своё исключение
            return AccessResult.success(null);
        }

        return AccessResult.success(userFavoriteDishRepository.save(UserFavoriteDish.fromDish(dish)));
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

    @Transactional
    public void deleteFavorite(Long favoriteId, Long userId) {
        userFavoriteDishRepository.findByIdAndUserId(favoriteId, userId)
                .ifPresent(fav -> {
                    // 1. Удаляем само блюдо
                    userFavoriteDishRepository.delete(fav);

                    // 2. Освобождаем слот (уменьшаем счетчик)
                    userFeatureUsageService.decrementUsage(
                            userId,
                            BotIdentifier.CALORIE_BOT, // Или возьми из контекста/конфига
                            BotFeature.DISH_FAVORITE_LIST
                    );
                });
    }

    public UserFavoriteDish getUserFavoriteDishByIdOrNull(Long id) {
        return userFavoriteDishRepository.findById(id).orElse(null);
    }
}
