package com.kuklin.manageapp.common.services;

import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.repositories.TelegramUserRepository;
import com.kuklin.manageapp.payment.services.GenerationBalanceService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelegramUserServiceFastPathTest {
    private static final Long TG_ID = 100L;
    private static final BotIdentifier BOT = BotIdentifier.CALORIE_BOT;

    private final TelegramUserRepository repository = mock(TelegramUserRepository.class);
    private final GenerationBalanceService balanceService = mock(GenerationBalanceService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TelegramUserService service = new TelegramUserService(repository, balanceService,
            mock(AppUserService.class), mock(UserAuthIdentityService.class), transactionManager);

    @Test
    void existingUserReturnedWithoutTransactionAndWrites() {
        TelegramUser existing = new TelegramUser().setTelegramId(TG_ID).setAppUserId(5L).setIsBotBlocked(false);
        when(repository.findTelegramUserByBotIdentifierAndTelegramId(BOT, TG_ID)).thenReturn(Optional.of(existing));

        assertThat(service.createOrGetUserByTelegram(BOT, telegramUser())).isSameAs(existing);

        verifyNoInteractions(transactionManager, balanceService);
        verify(repository, never()).save(any());
    }

    @Test
    void blockedUserGoesThroughTransactionAndIsUnblocked() {
        TelegramUser blocked = new TelegramUser().setTelegramId(TG_ID).setAppUserId(5L).setIsBotBlocked(true);
        when(repository.findTelegramUserByBotIdentifierAndTelegramId(BOT, TG_ID)).thenReturn(Optional.of(blocked));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TelegramUser result = service.createOrGetUserByTelegram(BOT, telegramUser());

        assertThat(result.getIsBotBlocked()).isFalse();
        verify(balanceService).createNewBalanceIfNotExist(5L, BOT);
    }

    private static User telegramUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(TG_ID);
        return user;
    }
}
