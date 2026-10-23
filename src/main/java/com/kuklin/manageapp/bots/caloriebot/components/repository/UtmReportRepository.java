package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmAttribution;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Запросы отчёта по источникам (UtmReportService). Только чтение.
 * <p>
 * Новый пользователь калорийного бота — тот, кому выдан пробный период (тариф SYSTEM_FREE или ARCHIVED_SYSTEM_FREE):
 * он выдаётся один раз, при первом /start. Так считаются и пришедшие сами, без UTM-ссылки.
 * Алиасы в кавычках: Postgres приводит имена без кавычек к нижнему регистру, а проекции ищут их по имени геттера.
 */
public interface UtmReportRepository extends Repository<UtmAttribution, Long> {

    /**
     * Воронка по источникам для пришедших за [from, to): что каждый сделал к сегодняшнему дню (когорта, а не
     * события периода). code = null — пришёл без UTM-ссылки. «Активный» — блюда в activeDays разных дней (UTC)
     * за первые windowDays после прихода. Оплаты — успешные платежи калорийного бота за всё время.
     */
    @Query(value = """
            WITH new_users AS (
                SELECT s.app_user_id, min(s.created_at) AS joined_at
                FROM user_subscription s
                         JOIN pricing_plans pp ON pp.id = s.pricing_plan_id
                WHERE s.bot_identifier = 'CALORIE_BOT'
                  AND pp.plan_status IN ('SYSTEM_FREE', 'ARCHIVED_SYSTEM_FREE')
                GROUP BY s.app_user_id
                HAVING min(s.created_at) >= :from AND min(s.created_at) < :to
            ),
            paid AS (
                SELECT p.app_user_id, sum(p.stars_amount) AS stars
                FROM payments p
                WHERE p.bot_identifier = 'CALORIE_BOT' AND p.status = 'SUCCESS'
                GROUP BY p.app_user_id
            )
            SELECT l.code AS "code",
                   count(*) AS "newUsers",
                   count(us.language) AS "languageChosen",
                   count(*) FILTER (WHERE EXISTS (SELECT 1 FROM dishes d WHERE d.user_id = n.app_user_id)) AS "firstDish",
                   count(*) FILTER (WHERE (SELECT count(DISTINCT cast(d.created AS date)) FROM dishes d
                                          WHERE d.user_id = n.app_user_id
                                            AND d.created >= n.joined_at
                                            AND d.created < n.joined_at + make_interval(days => :windowDays))
                                         >= :activeDays) AS "active",
                   count(paid.app_user_id) AS "paid",
                   cast(coalesce(sum(paid.stars), 0) AS bigint) AS "stars"
            FROM new_users n
                     LEFT JOIN utm_attributions a ON a.app_user_id = n.app_user_id
                     LEFT JOIN utm_links l ON l.id = a.utm_link_id
                     LEFT JOIN user_settings us ON us.user_id = n.app_user_id
                     LEFT JOIN paid ON paid.app_user_id = n.app_user_id
            GROUP BY l.code
            """, nativeQuery = true)
    List<SourceFunnelRow> findSourceFunnels(@Param("from") Instant from,
                                            @Param("to") Instant to,
                                            @Param("activeDays") int activeDays,
                                            @Param("windowDays") int windowDays);

    /** Переходы по ссылкам за [from, to) — всех, не только новых. */
    @Query(value = """
            SELECT l.code AS "code", count(*) AS "clicks"
            FROM utm_clicks c
                     JOIN utm_links l ON l.id = c.utm_link_id
            WHERE c.clicked_at >= :from AND c.clicked_at < :to
            GROUP BY l.code
            """, nativeQuery = true)
    List<CodeClicksRow> findClicks(@Param("from") Instant from, @Param("to") Instant to);

    /** Успешные оплаты калорийного бота за [from, to) — все, не только новых. */
    @Query(value = """
            SELECT count(*) AS "payments", cast(coalesce(sum(p.stars_amount), 0) AS bigint) AS "stars"
            FROM payments p
            WHERE p.bot_identifier = 'CALORIE_BOT' AND p.status = 'SUCCESS'
              AND p.paid_at >= :from AND p.paid_at < :to
            """, nativeQuery = true)
    PaymentsRow findPayments(@Param("from") Instant from, @Param("to") Instant to);

    /** Бонусные подписки (награды за приглашение), выданные за [from, to), кроме отменённых: сколько и на сколько дней. */
    @Query(value = """
            SELECT pp.code_for_order_id AS "planCode",
                   count(*) AS "grants",
                   cast(coalesce(round(sum(extract(EPOCH FROM (s.end_at - s.start_at)) / 86400)), 0) AS bigint) AS "days"
            FROM user_subscription s
                     JOIN pricing_plans pp ON pp.id = s.pricing_plan_id
            WHERE pp.plan_status = 'SYSTEM_BONUS' AND s.bot_identifier = 'CALORIE_BOT'
              AND s.status <> 'CANCELLED'
              AND s.created_at >= :from AND s.created_at < :to
            GROUP BY pp.code_for_order_id
            """, nativeQuery = true)
    List<BonusGrantsRow> findBonusGrants(@Param("from") Instant from, @Param("to") Instant to);

    /** Кто больше всех пригласил среди пришедших за [from, to). */
    @Query(value = """
            SELECT a.referrer_app_user_id AS "referrerAppUserId",
                   max(tu.username) AS "username",
                   count(*) AS "invited",
                   count(a.referrer_rewarded_at) AS "activated",
                   count(a.referrer_paid_rewarded_at) AS "paid"
            FROM utm_attributions a
                     LEFT JOIN telegram_users tu
                               ON tu.app_user_id = a.referrer_app_user_id AND tu.bot_identifier = 'CALORIE_BOT'
            WHERE a.referrer_app_user_id IS NOT NULL
              AND a.created_at >= :from AND a.created_at < :to
            GROUP BY a.referrer_app_user_id
            ORDER BY count(*) DESC, count(a.referrer_rewarded_at) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TopReferrerRow> findTopReferrers(@Param("from") Instant from, @Param("to") Instant to,
                                          @Param("limit") int limit);

    interface SourceFunnelRow {
        String getCode();

        long getNewUsers();

        long getLanguageChosen();

        long getFirstDish();

        long getActive();

        long getPaid();

        long getStars();
    }

    interface CodeClicksRow {
        String getCode();

        long getClicks();
    }

    interface PaymentsRow {
        long getPayments();

        long getStars();
    }

    interface BonusGrantsRow {
        String getPlanCode();

        long getGrants();

        long getDays();
    }

    interface TopReferrerRow {
        Long getReferrerAppUserId();

        String getUsername();

        long getInvited();

        long getActivated();

        long getPaid();
    }
}
