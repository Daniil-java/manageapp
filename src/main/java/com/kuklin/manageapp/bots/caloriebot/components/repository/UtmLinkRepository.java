package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface UtmLinkRepository extends JpaRepository<UtmLink, Long> {
    Optional<UtmLink> findByCode(String code);

    /**
     * Создаёт ссылку, если кода ещё нет. При параллельном создании проигравший не получает ошибку
     * (ON CONFLICT по UNIQUE(code)). created_at — DEFAULT из схемы.
     * @Transactional здесь: getOrCreateReferralLink зовут вне транзакции, а @Modifying без неё падает.
     *
     * @return 1 — создали, 0 — уже была
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO utm_links (code, owner_type, creator_id)
            VALUES (:code, :ownerType, :creatorId)
            ON CONFLICT (code) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("code") String code,
                       @Param("ownerType") String ownerType,
                       @Param("creatorId") Long creatorId);
}
