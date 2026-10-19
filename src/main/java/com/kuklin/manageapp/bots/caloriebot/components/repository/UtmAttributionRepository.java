package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmAttribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
