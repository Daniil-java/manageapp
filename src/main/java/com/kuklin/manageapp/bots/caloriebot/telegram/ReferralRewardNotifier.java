package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.components.services.ReferralRewardService;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReferralRewardService.ReferralReward;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.models.events.DishAddedEvent;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.payment.models.SubscriptionPurchasedEvent;
import com.kuklin.manageapp.payment.models.SubscriptionRefundedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Блюдо добавлено, подписка куплена или за неё вернули деньги — выдать или отменить награды за приглашение
 * (ReferralRewardService) и сообщить о них получателям на их языке.
 * <p>
 * После коммита блюда или платежа: их откат не должен оставить выданную награду. fallbackExecution — событие
 * могли опубликовать и вне транзакции. Сбой награды или отправки только логируется: блюдо и платёж уже записаны.
 * <p>
 * @Async — в общем пуле Spring, не в потоке апдейта. В потоке апдейта слушатель после коммита ещё держит
 * соединение транзакции блюда (Hibernate отдаёт его только при закрытии EntityManager), а награда открывает
 * свою транзакцию (REQUIRES_NEW) — второе соединение на каждое блюдо. 16 потоков апдейтов против 10 соединений
 * пула могли повиснуть, ожидая друг друга. Заодно пользователь не ждёт выдачи наград и сообщений другу.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReferralRewardNotifier {

    private final ReferralRewardService referralRewardService;
    private final TelegramUserService telegramUserService;
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onDishAdded(DishAddedEvent event) {
        List<ReferralReward> rewards;
        try {
            rewards = referralRewardService.rewardDishAdded(event.appUserId());
        } catch (Exception e) {
            log.error("Referral dish reward failed for user {}", event.appUserId(), e);
            return;
        }
        rewards.forEach(this::notify);
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onSubscriptionPurchased(SubscriptionPurchasedEvent event) {
        if (event.botIdentifier() != BotIdentifier.CALORIE_BOT) {
            return;
        }
        ReferralReward reward;
        try {
            reward = referralRewardService.rewardFriendPaymentOrNull(event.appUserId(), event.paymentId());
        } catch (Exception e) {
            log.error("Referral payment reward failed for user {}", event.appUserId(), e);
            return;
        }
        if (reward != null) {
            notify(reward);
        }
    }

    /** Другу вернули деньги за подписку — отменить награду пригласившего за эту оплату. */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onSubscriptionRefunded(SubscriptionRefundedEvent event) {
        if (event.botIdentifier() != BotIdentifier.CALORIE_BOT) {
            return;
        }
        ReferralReward revoked;
        try {
            revoked = referralRewardService.revokeFriendPaymentRewardOrNull(event.appUserId(), event.paymentId());
        } catch (Exception e) {
            log.error("Referral payment reward revoke failed for user {}, payment {}",
                    event.appUserId(), event.paymentId(), e);
            return;
        }
        if (revoked != null) {
            notify(revoked);
        }
    }

    private void notify(ReferralReward reward) {
        Long recipient = reward.recipientAppUserId();
        String text = switch (reward.type()) {
            case INVITED_FIRST_DISH -> texts.get(recipient, BotTextKeys.REFERRAL_REWARD_INVITED, reward.days());
            case INVITER_FRIEND_ACTIVE -> buildFriendActiveTextOrNull(reward);
            case INVITER_FRIEND_PAID -> texts.get(recipient, BotTextKeys.REFERRAL_REWARD_FRIEND_PAID, reward.days());
            case INVITER_FRIEND_PAID_REVOKED ->
                    texts.get(recipient, BotTextKeys.REFERRAL_REWARD_FRIEND_PAID_REVOKED, reward.days());
        };
        if (text != null) {
            sendOrLog(recipient, text);
        }
    }

    /** null — дней не выдано и лимит не набран (тарифа нет в БД): сообщать нечего. */
    private String buildFriendActiveTextOrNull(ReferralReward reward) {
        Long recipient = reward.recipientAppUserId();
        if (reward.days() > 0) {
            return texts.get(recipient, BotTextKeys.REFERRAL_REWARD_INVITER,
                    reward.days(), referralRewardService.getActivationDays());
        }
        if (reward.limitReached()) {
            return texts.get(recipient, BotTextKeys.REFERRAL_REWARD_INVITER_LIMIT,
                    referralRewardService.getInviterLimitDays());
        }
        return null;
    }

    private void sendOrLog(Long appUserId, String text) {
        try {
            TelegramUser user = telegramUserService
                    .findByAppUserIdAndBotIdentifier(appUserId, BotIdentifier.CALORIE_BOT)
                    .orElse(null);
            if (user == null || Boolean.TRUE.equals(user.getIsBotBlocked())) {
                log.info("Referral reward message not sent, no active chat: user {}", appUserId);
                return;
            }
            calorieTelegramBot.sendReturnedMessage(user.getTelegramId(), text);
        } catch (Exception e) {
            log.error("Referral reward message failed for user {}", appUserId, e);
        }
    }
}
