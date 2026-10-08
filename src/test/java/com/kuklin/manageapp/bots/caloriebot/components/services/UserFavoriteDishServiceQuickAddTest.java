package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UserFavoriteDishRepository;
import com.kuklin.manageapp.bots.caloriebot.entities.UserFavoriteDish;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.UserFavoriteDishDto;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Быстрый набор избранного: закреплённые идут первыми по месту, максимум 4, открепление сдвигает остальных.
 */
@SuppressWarnings("unchecked")
class UserFavoriteDishServiceQuickAddTest {

    private static final Long USER_ID = 7L;

    private UserFavoriteDishService service;
    // Порядок, в котором репозиторий отдаёт избранное: по последнему использованию
    private final List<UserFavoriteDish> byLastUse = new ArrayList<>();

    @BeforeEach
    void setUp() {
        UserFavoriteDishRepository repository = mock(UserFavoriteDishRepository.class);
        service = new UserFavoriteDishService(repository, mock(DishService.class),
                mock(UserFeatureUsageService.class), mock(ObjectProvider.class));

        for (long id = 1; id <= 6; id++) {
            byLastUse.add(new UserFavoriteDish().setId(id).setUserId(USER_ID).setName("dish " + id));
        }
        when(repository.findAllByUserIdOrderByLastUsedAtDescCreatedAtDesc(USER_ID)).thenReturn(byLastUse);
        when(repository.findByIdAndUserId(anyLong(), eq(USER_ID))).thenAnswer(inv -> byLastUse.stream()
                .filter(f -> f.getId().equals(inv.getArgument(0)))
                .findFirst());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static List<Long> ids(List<UserFavoriteDishDto> list) {
        return list.stream().map(UserFavoriteDishDto::getId).toList();
    }

    @Test
    void pinnedGoFirstInPinOrderThenTheRestByLastUse() {
        service.setQuickAdd(USER_ID, 5L, true);
        List<UserFavoriteDishDto> result = service.setQuickAdd(USER_ID, 3L, true);

        assertThat(ids(result)).containsExactly(5L, 3L, 1L, 2L, 4L, 6L);
        assertThat(result.get(0).getQuickAddPosition()).isEqualTo(1);
        assertThat(result.get(1).getQuickAddPosition()).isEqualTo(2);
        assertThat(result.get(2).getQuickAddPosition()).isNull();
    }

    @Test
    void fifthPinIsRejected() {
        for (long id = 1; id <= 4; id++) {
            service.setQuickAdd(USER_ID, id, true);
        }

        assertThatThrownBy(() -> service.setQuickAdd(USER_ID, 5L, true))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        e -> assertThat(e.getErrorStatus()).isEqualTo(ErrorStatus.QUICK_ADD_FULL));
    }

    @Test
    void unpinClosesTheGap() {
        service.setQuickAdd(USER_ID, 4L, true);
        service.setQuickAdd(USER_ID, 2L, true);
        service.setQuickAdd(USER_ID, 6L, true);

        List<UserFavoriteDishDto> result = service.setQuickAdd(USER_ID, 2L, false);

        assertThat(ids(result).subList(0, 2)).containsExactly(4L, 6L);
        assertThat(result.get(1).getQuickAddPosition()).isEqualTo(2);
        assertThat(byLastUse.get(1).getQuickAddPosition()).isNull();
    }

    @Test
    void pinningTwiceKeepsThePlace() {
        service.setQuickAdd(USER_ID, 3L, true);
        service.setQuickAdd(USER_ID, 1L, true);

        List<UserFavoriteDishDto> result = service.setQuickAdd(USER_ID, 3L, true);

        assertThat(ids(result).subList(0, 2)).containsExactly(3L, 1L);
    }

    @Test
    void unknownFavoriteIsNotFound() {
        assertThatThrownBy(() -> service.setQuickAdd(USER_ID, 99L, true))
                .isInstanceOf(ErrorResponseException.class);
    }
}
