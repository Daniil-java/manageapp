package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmAttribution;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UtmAttributionRepository extends JpaRepository<UtmAttribution, Long> {

    /**
     * Первое касание пишется один раз: повторный /start по другой ссылке его не перезаписывает (ON CONFLICT).
     *
     * @return 1 — записали, 0 — у пользователя уже есть источник
     */
    @Modifying
    @Query(value = """
            INSERT INTO utm_attributions (app_user_id, utm_link_id, referrer_app_user_id)
            VALUES (:appUserId, :utmLinkId, :referrerAppUserId)
            ON CONFLICT (app_user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("appUserId") Long appUserId,
                       @Param("utmLinkId") Long utmLinkId,
                       @Param("referrerAppUserId") Long referrerAppUserId);

    /**
     * Приглашённый, которому за блюда ещё положена награда: себе за первое блюдо или пригласившему —
     * пока не прошли первые дни (пришёл после activeSince). С блокировкой строки: блюда, сохранённые
     * одновременно, не выдадут награду дважды. Остальных (не приглашённых, всё получивших) не блокирует.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from UtmAttribution a
            where a.appUserId = :appUserId and a.referrerAppUserId is not null
              and (a.invitedRewardedAt is null or (a.referrerRewardedAt is null and a.createdAt > :activeSince))
            """)
    Optional<UtmAttribution> findDishRewardPendingForUpdate(@Param("appUserId") Long appUserId,
                                                            @Param("activeSince") Instant activeSince);

    /** Приглашённый, за чью оплату пригласивший ещё не награждён, — с блокировкой строки. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from UtmAttribution a
            where a.appUserId = :appUserId and a.referrerAppUserId is not null and a.referrerPaidRewardedAt is null
            """)
    Optional<UtmAttribution> findPaymentRewardPendingForUpdate(@Param("appUserId") Long appUserId);

    /** Приглашённый, за чей платёж paymentId пригласивший награждён, — с блокировкой строки (отмена при возврате). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from UtmAttribution a
            where a.appUserId = :appUserId and a.referrerPaidPaymentId = :paymentId
            """)
    Optional<UtmAttribution> findPaymentRewardForUpdate(@Param("appUserId") Long appUserId,
                                                        @Param("paymentId") Long paymentId);

    /** Сколько дней Premium пригласивший получил за активных друзей начиная с since (без наград за оплату). */
    @Query("""
            select coalesce(sum(a.referrerBonusDays), 0L) from UtmAttribution a
            where a.referrerAppUserId = :referrerAppUserId and a.referrerRewardedAt >= :since
            """)
    long sumReferrerBonusDaysSince(@Param("referrerAppUserId") Long referrerAppUserId,
                                   @Param("since") Instant since);

    /** Пришли по реферальной ссылке пользователя. */
    long countByReferrerAppUserId(Long referrerAppUserId);

    /** Из них вели дневник нужное число дней. */
    long countByReferrerAppUserIdAndReferrerRewardedAtIsNotNull(Long referrerAppUserId);

    /** Из них оплатили подписку. */
    long countByReferrerAppUserIdAndReferrerPaidRewardedAtIsNotNull(Long referrerAppUserId);

    /** Сколько всего дней Premium пользователь получил за друзей (за активных и за оплативших). */
    @Query("""
            select coalesce(sum(coalesce(a.referrerBonusDays, 0) + coalesce(a.referrerPaidBonusDays, 0)), 0L)
            from UtmAttribution a
            where a.referrerAppUserId = :referrerAppUserId
            """)
    long sumReferrerBonusDays(@Param("referrerAppUserId") Long referrerAppUserId);
}
