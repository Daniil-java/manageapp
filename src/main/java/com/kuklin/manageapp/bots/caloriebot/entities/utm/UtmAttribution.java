package com.kuklin.manageapp.bots.caloriebot.entities.utm;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * Откуда пришёл пользователь: UTM-ссылка, по которой он впервые запустил бота. Одна строка на пользователя,
 * пишется один раз (UtmAttributionRepository.insertIfAbsent). У приглашённых — ещё и отметки о наградах
 * за приглашение (ReferralRewardService): каждая награда выдаётся один раз.
 */
@Entity
@Table(name = "utm_attributions")
@Data
@Accessors(chain = true)
public class UtmAttribution {
    @Id
    private Long appUserId;

    private Long utmLinkId;

    // Кто пригласил — владелец реферальной ссылки. null — пришёл не по реферальной
    private Long referrerAppUserId;

    // Когда пользователь пришёл. От него отсчитываются первые 7 дней для награды пригласившему
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    // Приглашённый получил награду за первое блюдо
    private Instant invitedRewardedAt;

    // Друг вёл дневник нужное число дней — пригласивший награждён
    private Instant referrerRewardedAt;

    // Сколько дней за это получил пригласивший: меньше полного срока или 0 — упёрся в лимит за 30 дней
    private Integer referrerBonusDays;

    // Друг оплатил подписку — пригласивший награждён
    private Instant referrerPaidRewardedAt;

    private Integer referrerPaidBonusDays;

    // За какой платёж друга выдана награда: за него вернули деньги — награда отменяется
    private Long referrerPaidPaymentId;

    // Бонусная подписка пригласившего за оплату друга — её отменяют при возврате денег
    private Long referrerPaidSubscriptionId;
}
