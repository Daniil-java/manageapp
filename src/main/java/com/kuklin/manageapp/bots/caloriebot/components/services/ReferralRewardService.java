package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmAttributionRepository;
import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmAttribution;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.services.PricingPlanService;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Награды за приглашение (пришёл по ссылке ref<id>, см. UtmService.processClick). Три награды, каждая — один раз:
 * <ul>
 *     <li>приглашённому — CALORIE_REFERRAL_INVITED (3 дня) за первое блюдо: дёшево и подталкивает начать;</li>
 *     <li>пригласившему — CALORIE_REFERRAL_INVITER (7 дней), когда друг вёл дневник activation-days (3) разных
 *     дня за первые activation-window-days (7) дней. Не за первое блюдо: одно фото с фейкового аккаунта
 *     награду не принесёт. Не больше inviter-limit-days (14) дней за последние 30 дней — бесконечный
 *     бесплатный Premium только у того, кто платит;</li>
 *     <li>пригласившему — CALORIE_REFERRAL_FRIEND_PAID (30 дней) за первую оплату подписки другом. Без лимита:
 *     эта награда сама себя окупает.</li>
 * </ul>
 * Сроки — у тарифов в pricing_plans.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralRewardService {

    public static final String INVITED_PLAN_CODE = "CALORIE_REFERRAL_INVITED";
    public static final String INVITER_PLAN_CODE = "CALORIE_REFERRAL_INVITER";
    public static final String FRIEND_PAID_PLAN_CODE = "CALORIE_REFERRAL_FRIEND_PAID";
    /** Бонус по партнёрской ссылке; срок выдачи — utm_links.bonus_days. */
    public static final String PARTNER_BONUS_PLAN_CODE = "CALORIE_PARTNER_BONUS";
    static final Duration INVITER_LIMIT_PERIOD = Duration.ofDays(30);

    private final UtmAttributionRepository utmAttributionRepository;
    private final PricingPlanService pricingPlanService;
    private final UserSubscriptionService userSubscriptionService;
    private final StreakService streakService;

    /** Сколько разных дней с блюдами нужно другу, чтобы пригласивший получил награду. */
    @Value("${calorie.referral.activation-days:3}")
    private int activationDays;

    /** За сколько первых дней после прихода друг должен их набрать. */
    @Value("${calorie.referral.activation-window-days:7}")
    private int activationWindowDays;

    /** Максимум дней пригласившему за активных друзей за последние 30 дней. */
    @Value("${calorie.referral.inviter-limit-days:14}")
    private int inviterLimitDays;

    /**
     * Приглашённый добавил блюдо: награда ему за первое блюдо и пригласившему — если друг набрал нужные дни.
     * <p>
     * REQUIRES_NEW: зовётся после коммита сохранения блюда (DishAddedEvent), когда транзакция блюда
     * уже закрыта, — без своей транзакции изменения не сохранились бы.
     *
     * @return выданные награды, пусто — ничего не положено
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ReferralReward> rewardDishAdded(Long appUserId) {
        Instant now = Instant.now();
        Duration window = Duration.ofDays(activationWindowDays);
        UtmAttribution attribution = utmAttributionRepository
                .findDishRewardPendingForUpdate(appUserId, now.minus(window))
                .orElse(null);
        if (attribution == null) {
            return List.of();
        }
        List<ReferralReward> rewards = new ArrayList<>();
        Long referrerAppUserId = attribution.getReferrerAppUserId();

        if (attribution.getInvitedRewardedAt() == null) {
            int days = grant(appUserId, INVITED_PLAN_CODE, Integer.MAX_VALUE).days();
            attribution.setInvitedRewardedAt(now);
            if (days > 0) {
                rewards.add(new ReferralReward(RewardType.INVITED_FIRST_DISH, appUserId, days, false));
            }
        }

        Instant joinedAt = attribution.getCreatedAt();
        if (attribution.getReferrerRewardedAt() == null && joinedAt.isAfter(now.minus(window))
                && streakService.countDishDaysBetween(appUserId, joinedAt, joinedAt.plus(window)) >= activationDays) {
            long alreadyGiven = utmAttributionRepository
                    .sumReferrerBonusDaysSince(referrerAppUserId, now.minus(INVITER_LIMIT_PERIOD));
            int limitLeft = (int) Math.max(0, inviterLimitDays - alreadyGiven);
            int days = grant(referrerAppUserId, INVITER_PLAN_CODE, limitLeft).days();
            attribution.setReferrerRewardedAt(now).setReferrerBonusDays(days);
            rewards.add(new ReferralReward(RewardType.INVITER_FRIEND_ACTIVE, referrerAppUserId, days, limitLeft == 0));
            log.info("Referral activation: user {} active, referrer {} +{} days (given in period: {})",
                    appUserId, referrerAppUserId, days, alreadyGiven);
        }

        utmAttributionRepository.save(attribution);
        return rewards;
    }

    /**
     * Приглашённый купил подписку — пригласившему награда за первую оплату. Запоминается платёж и бонусная
     * подписка: если за этот платёж вернут деньги, награда отменяется (revokeFriendPaymentRewardOrNull).
     *
     * @return награда или null — пользователь не приглашённый или за оплату уже награждали
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReferralReward rewardFriendPaymentOrNull(Long appUserId, Long paymentId) {
        UtmAttribution attribution = utmAttributionRepository.findPaymentRewardPendingForUpdate(appUserId).orElse(null);
        if (attribution == null) {
            return null;
        }
        Long referrerAppUserId = attribution.getReferrerAppUserId();
        Grant grant = grant(referrerAppUserId, FRIEND_PAID_PLAN_CODE, Integer.MAX_VALUE);
        attribution.setReferrerPaidRewardedAt(Instant.now())
                .setReferrerPaidBonusDays(grant.days())
                .setReferrerPaidPaymentId(paymentId)
                .setReferrerPaidSubscriptionId(grant.subscriptionId());
        utmAttributionRepository.save(attribution);
        log.info("Referral payment: user {} paid ({}), referrer {} +{} days",
                appUserId, paymentId, referrerAppUserId, grant.days());
        return grant.days() > 0
                ? new ReferralReward(RewardType.INVITER_FRIEND_PAID, referrerAppUserId, grant.days(), false)
                : null;
    }

    /**
     * За платёж приглашённого вернули деньги — отменить награду пригласившего за эту оплату: бонусная подписка
     * отменяется (уже прошедшие дни не возвращаются, остаток сгорает), отметка снимается — следующая настоящая
     * оплата снова принесёт награду.
     *
     * @return отменённая награда (days — сколько дней было выдано) или null — за этот платёж награды не было
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReferralReward revokeFriendPaymentRewardOrNull(Long appUserId, Long paymentId) {
        UtmAttribution attribution = utmAttributionRepository.findPaymentRewardForUpdate(appUserId, paymentId).orElse(null);
        if (attribution == null) {
            return null;
        }
        Long referrerAppUserId = attribution.getReferrerAppUserId();
        int days = attribution.getReferrerPaidBonusDays() == null ? 0 : attribution.getReferrerPaidBonusDays();
        boolean cancelled = attribution.getReferrerPaidSubscriptionId() != null
                && userSubscriptionService.cancelBonusSubscription(attribution.getReferrerPaidSubscriptionId());
        attribution.setReferrerPaidRewardedAt(null)
                .setReferrerPaidBonusDays(null)
                .setReferrerPaidPaymentId(null)
                .setReferrerPaidSubscriptionId(null);
        utmAttributionRepository.save(attribution);
        log.info("Referral payment refunded: user {} ({}), referrer {} bonus revoked ({} days, subscription cancelled: {})",
                appUserId, paymentId, referrerAppUserId, days, cancelled);
        return days > 0 ? new ReferralReward(RewardType.INVITER_FRIEND_PAID_REVOKED, referrerAppUserId, days, false) : null;
    }

    /**
     * Выдать бонусный тариф на его срок, но не больше maxDays.
     *
     * @return выданное: 0 дней и подписки нет — лимит исчерпан или тарифа нет в БД
     */
    private Grant grant(Long appUserId, String planCode, int maxDays) {
        PricingPlan plan = pricingPlanService.getPricingPlanByCodeOrNull(planCode);
        if (plan == null || plan.getDurationDays() == null) {
            log.error("Referral bonus plan {} not found", planCode);
            return Grant.NONE;
        }
        int days = Math.min(plan.getDurationDays(), maxDays);
        if (days <= 0) {
            return Grant.NONE;
        }
        UserSubscription sub = userSubscriptionService.createBonusSubscription(appUserId, BotIdentifier.CALORIE_BOT, plan, days);
        return new Grant(days, sub == null ? null : sub.getId());
    }

    private record Grant(int days, Long subscriptionId) {
        static final Grant NONE = new Grant(0, null);
    }

    /**
     * Бонус по партнёрской ссылке (utm_links.bonus_days): новичок получает столько дней Premium, они встают в очередь
     * за пробным периодом. Положен ли — решает UtmService.processClick, выдача — здесь: в UtmService зависимость
     * от подписок замкнула бы круг бинов (подписки → бот-реестр → бот канала → UtmService).
     *
     * @return сколько дней выдано: 0 — не положено или тарифа CALORIE_PARTNER_BONUS нет в БД
     */
    @Transactional
    public int grantPartnerBonusOrZero(Long appUserId, int days) {
        if (days <= 0) {
            return 0;
        }
        PricingPlan plan = pricingPlanService.getPricingPlanByCodeOrNull(PARTNER_BONUS_PLAN_CODE);
        if (plan == null) {
            log.error("Partner bonus plan {} not found", PARTNER_BONUS_PLAN_CODE);
            return 0;
        }
        userSubscriptionService.createBonusSubscription(appUserId, BotIdentifier.CALORIE_BOT, plan, days);
        return days;
    }

    /** Срок бонусного тарифа в днях, 0 — тарифа нет в БД. */
    public int getPlanDays(String planCode) {
        PricingPlan plan = pricingPlanService.getPricingPlanByCodeOrNull(planCode);
        return plan == null || plan.getDurationDays() == null ? 0 : plan.getDurationDays();
    }

    public int getActivationWindowDays() {
        return activationWindowDays;
    }

    public int getActivationDays() {
        return activationDays;
    }

    public int getInviterLimitDays() {
        return inviterLimitDays;
    }

    /** Счётчики для экрана «Пригласить друга». */
    @Transactional(readOnly = true)
    public ReferralStats getStats(Long appUserId) {
        return new ReferralStats(
                utmAttributionRepository.countByReferrerAppUserId(appUserId),
                utmAttributionRepository.countByReferrerAppUserIdAndReferrerRewardedAtIsNotNull(appUserId),
                utmAttributionRepository.countByReferrerAppUserIdAndReferrerPaidRewardedAtIsNotNull(appUserId),
                utmAttributionRepository.sumReferrerBonusDays(appUserId));
    }

    public enum RewardType {
        /** Приглашённому — за первое блюдо */
        INVITED_FIRST_DISH,
        /** Пригласившему — друг вёл дневник нужное число дней */
        INVITER_FRIEND_ACTIVE,
        /** Пригласившему — друг оплатил подписку */
        INVITER_FRIEND_PAID,
        /** У пригласившего отменена награда за оплату: другу вернули деньги */
        INVITER_FRIEND_PAID_REVOKED
    }

    /**
     * @param recipientAppUserId кто получил награду
     * @param days               сколько дней выдано (0 — лимит пригласившего набран)
     * @param limitReached       лимит дней за 30 дней набран (только INVITER_FRIEND_ACTIVE)
     */
    public record ReferralReward(RewardType type, Long recipientAppUserId, int days, boolean limitReached) {
    }

    /**
     * @param invited      пришли по ссылке
     * @param activated    из них вели дневник нужное число дней
     * @param paid         из них оплатили подписку
     * @param receivedDays сколько дней Premium получено за друзей
     */
    public record ReferralStats(long invited, long activated, long paid, long receivedDays) {
    }
}
