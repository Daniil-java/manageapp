package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface UtmClickRepository extends JpaRepository<UtmClick, Long> {
    long countByUtmLinkId(Long utmLinkId);

    // переходы по ссылкам с кодом по шаблону (zch% — посты канала)
    @Query(value = """
            SELECT count(*) FROM utm_clicks c JOIN utm_links l ON l.id = c.utm_link_id
            WHERE l.code LIKE :pattern AND c.clicked_at >= :since
            """, nativeQuery = true)
    long countByCodePrefixSince(@Param("pattern") String pattern, @Param("since") Instant since);

    @Query(value = """
            SELECT count(*) FROM utm_clicks c JOIN utm_links l ON l.id = c.utm_link_id
            WHERE l.code LIKE :pattern AND c.clicked_at >= :since AND c.is_new_user
            """, nativeQuery = true)
    long countNewByCodePrefixSince(@Param("pattern") String pattern, @Param("since") Instant since);
}
