package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmAttributionRepository;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReferralRewardService.ReferralReward;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReferralRewardService.RewardType;
import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmAttribution;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.services.PricingPlanService;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReferralRewardServiceTest {

    private static final long INVITED = 100L;
    private static final long REFERRER = 7L;
    private static final long PAYMENT_ID = 55L;
    private static final long BONUS_SUBSCRIPTION_ID = 900L;

    private final UtmAttributionRepository repository = mock(UtmAttributionRepository.class);
    private final PricingPlanService pricingPlanService = mock(PricingPlanService.class);
    private final UserSubscriptionService subscriptionService = mock(UserSubscriptionService.class);
    private final StreakService streakService = mock(StreakService.class);
    private final ReferralRewardService service =
            new ReferralRewardService(repository, pricingPlanService, subscriptionService, streakService);

    private final PricingPlan invitedPlan = plan(1L, ReferralRewardService.INVITED_PLAN_CODE, 3);
    private final PricingPlan inviterPlan = plan(2L, ReferralRewardService.INVITER_PLAN_CODE, 7);
    private final PricingPlan paidPlan = plan(3L, ReferralRewardService.FRIEND_PAID_PLAN_CODE, 30);
    private UtmAttribution attribution;

    private static PricingPlan plan(long id, String code, int days) {
        return new PricingPlan().setId(id).setCodeForOrderId(code).setDurationDays(days);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "activationDays", 3);
        ReflectionTestUtils.setField(service, "activationWindowDays", 7);
        ReflectionTestUtils.setField(service, "inviterLimitDays", 14);
        for (PricingPlan plan : List.of(invitedPlan, inviterPlan, paidPlan)) {
            when(pricingPlanService.getPricingPlanByCodeOrNull(plan.getCodeForOrderId())).thenReturn(plan);
        }
        attribution = new UtmAttribution().setAppUserId(INVITED).setUtmLinkId(5L).setReferrerAppUserId(REFERRER)
                .setCreatedAt(Instant.now().minus(Duration.ofDays(2)));
        when(repository.findDishRewardPendingForUpdate(eq(INVITED), any())).thenReturn(Optional.of(attribution));
        when(repository.findPaymentRewardPendingForUpdate(INVITED)).thenReturn(Optional.of(attribution));
        when(subscriptionService.createBonusSubscription(anyLong(), any(), any(), anyInt()))
                .thenReturn(new UserSubscription().setId(BONUS_SUBSCRIPTION_ID));
    }

    private void givenDishDays(long days) {
        when(streakService.countDishDaysBetween(eq(INVITED), any(), any())).thenReturn(days);
    }

    private void givenReferrerAlreadyGot(long days) {
        when(repository.sumReferrerBonusDaysSince(eq(REFERRER), any())).thenReturn(days);
    }

    @Test
    void firstDishRewardsOnlyInvited() {
        givenDishDays(1);

        List<ReferralReward> rewards = service.rewardDishAdded(INVITED);

        assertThat(rewards).containsExactly(new ReferralReward(RewardType.INVITED_FIRST_DISH, INVITED, 3, false));
        verify(subscriptionService).createBonusSubscription(INVITED, BotIdentifier.CALORIE_BOT, invitedPlan, 3);
        verify(subscriptionService, never()).createBonusSubscription(eq(REFERRER), any(), any(), anyInt());
        assertThat(attribution.getInvitedRewardedAt()).isNotNull();
        assertThat(attribution.getReferrerRewardedAt()).isNull();
        verify(repository).save(attribution);
    }

    @Test
    void thirdDiaryDayRewardsReferrer() {
        attribution.setInvitedRewardedAt(Instant.now().minus(Duration.ofDays(2)));
        givenDishDays(3);
        givenReferrerAlreadyGot(0);

        List<ReferralReward> rewards = service.rewardDishAdded(INVITED);

        assertThat(rewards).containsExactly(new ReferralReward(RewardType.INVITER_FRIEND_ACTIVE, REFERRER, 7, false));
        verify(subscriptionService).createBonusSubscription(REFERRER, BotIdentifier.CALORIE_BOT, inviterPlan, 7);
        verify(subscriptionService, never()).createBonusSubscription(eq(INVITED), any(), any(), anyInt());
        assertThat(attribution.getReferrerRewardedAt()).isNotNull();
        assertThat(attribution.getReferrerBonusDays()).isEqualTo(7);
    }

    @Test
    void daysAreCountedInFirstWeekAfterJoining() {
        givenDishDays(3);
        givenReferrerAlreadyGot(0);
        Instant joinedAt = attribution.getCreatedAt();

        service.rewardDishAdded(INVITED);

        verify(streakService).countDishDaysBetween(INVITED, joinedAt, joinedAt.plus(Duration.ofDays(7)));
    }

    @Test
    void afterFirstWeekReferrerIsNotRewarded() {
        attribution.setInvitedRewardedAt(Instant.now()).setCreatedAt(Instant.now().minus(Duration.ofDays(8)));

        assertThat(service.rewardDishAdded(INVITED)).isEmpty();
        verify(streakService, never()).countDishDaysBetween(anyLong(), any(), any());
        verify(subscriptionService, never()).createBonusSubscription(anyLong(), any(), any(), anyInt());
    }

    @Test
    void referrerGetsOnlyWhatIsLeftOfLimit() {
        attribution.setInvitedRewardedAt(Instant.now());
        givenDishDays(3);
        givenReferrerAlreadyGot(10);

        List<ReferralReward> rewards = service.rewardDishAdded(INVITED);

        assertThat(rewards).containsExactly(new ReferralReward(RewardType.INVITER_FRIEND_ACTIVE, REFERRER, 4, false));
        verify(subscriptionService).createBonusSubscription(REFERRER, BotIdentifier.CALORIE_BOT, inviterPlan, 4);
    }

    @Test
    void referrerAtLimitGetsNothingButIsMarked() {
        attribution.setInvitedRewardedAt(Instant.now());
        givenDishDays(3);
        givenReferrerAlreadyGot(14);

        List<ReferralReward> rewards = service.rewardDishAdded(INVITED);

        assertThat(rewards).containsExactly(new ReferralReward(RewardType.INVITER_FRIEND_ACTIVE, REFERRER, 0, true));
        verify(subscriptionService, never()).createBonusSubscription(anyLong(), any(), any(), anyInt());
        // Отмечено — следующее блюдо награду не повторит
        assertThat(attribution.getReferrerRewardedAt()).isNotNull();
        assertThat(attribution.getReferrerBonusDays()).isZero();
    }

    @Test
    void nothingPendingNothingGiven() {
        when(repository.findDishRewardPendingForUpdate(eq(INVITED), any())).thenReturn(Optional.empty());

        assertThat(service.rewardDishAdded(INVITED)).isEmpty();
        verify(subscriptionService, never()).createBonusSubscription(anyLong(), any(), any(), anyInt());
        verify(repository, never()).save(any());
    }

    @Test
    void missingPlanGivesZeroDaysWithoutFailing() {
        givenDishDays(1);
        when(pricingPlanService.getPricingPlanByCodeOrNull(ReferralRewardService.INVITED_PLAN_CODE)).thenReturn(null);

        assertThat(service.rewardDishAdded(INVITED)).isEmpty();
        assertThat(attribution.getInvitedRewardedAt()).isNotNull();
    }

    @Test
    void friendPaymentRewardsReferrerWithoutLimit() {
        givenReferrerAlreadyGot(14);

        ReferralReward reward = service.rewardFriendPaymentOrNull(INVITED, PAYMENT_ID);

        assertThat(reward).isEqualTo(new ReferralReward(RewardType.INVITER_FRIEND_PAID, REFERRER, 30, false));
        verify(subscriptionService).createBonusSubscription(REFERRER, BotIdentifier.CALORIE_BOT, paidPlan, 30);
        assertThat(attribution.getReferrerPaidRewardedAt()).isNotNull();
        assertThat(attribution.getReferrerPaidBonusDays()).isEqualTo(30);
        assertThat(attribution.getReferrerPaidPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(attribution.getReferrerPaidSubscriptionId()).isEqualTo(BONUS_SUBSCRIPTION_ID);
        verify(repository).save(attribution);
    }

    @Test
    void refundRevokesPaymentRewardAndAllowsNextOne() {
        attribution.setReferrerPaidRewardedAt(Instant.now()).setReferrerPaidBonusDays(30)
                .setReferrerPaidPaymentId(PAYMENT_ID).setReferrerPaidSubscriptionId(BONUS_SUBSCRIPTION_ID);
        when(repository.findPaymentRewardForUpdate(INVITED, PAYMENT_ID)).thenReturn(Optional.of(attribution));
        when(subscriptionService.cancelBonusSubscription(BONUS_SUBSCRIPTION_ID)).thenReturn(true);

        ReferralReward revoked = service.revokeFriendPaymentRewardOrNull(INVITED, PAYMENT_ID);

        assertThat(revoked).isEqualTo(new ReferralReward(RewardType.INVITER_FRIEND_PAID_REVOKED, REFERRER, 30, false));
        verify(subscriptionService).cancelBonusSubscription(BONUS_SUBSCRIPTION_ID);
        // Отметка снята — следующая настоящая оплата снова принесёт награду
        assertThat(attribution.getReferrerPaidRewardedAt()).isNull();
        assertThat(attribution.getReferrerPaidPaymentId()).isNull();
        assertThat(attribution.getReferrerPaidSubscriptionId()).isNull();
        verify(repository).save(attribution);
    }

    @Test
    void refundOfOtherPaymentRevokesNothing() {
        when(repository.findPaymentRewardForUpdate(INVITED, 999L)).thenReturn(Optional.empty());

        assertThat(service.revokeFriendPaymentRewardOrNull(INVITED, 999L)).isNull();
        verify(subscriptionService, never()).cancelBonusSubscription(anyLong());
        verify(repository, never()).save(any());
    }

    @Test
    void secondPaymentIsNotRewarded() {
        when(repository.findPaymentRewardPendingForUpdate(INVITED)).thenReturn(Optional.empty());

        assertThat(service.rewardFriendPaymentOrNull(INVITED, PAYMENT_ID)).isNull();
        verify(subscriptionService, never()).createBonusSubscription(anyLong(), any(), any(), anyInt());
    }

    @Test
    void planDays() {
        assertThat(service.getPlanDays(ReferralRewardService.FRIEND_PAID_PLAN_CODE)).isEqualTo(30);
        assertThat(service.getPlanDays("UNKNOWN")).isZero();
    }
}
