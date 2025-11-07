package com.kuklin.manageapp.bots.hhparserbot.repositories;

import com.kuklin.manageapp.bots.hhparserbot.entities.HhSkill;
import com.kuklin.manageapp.bots.hhparserbot.models.SkillSource;
import com.kuklin.manageapp.bots.hhparserbot.models.SkillSummaryRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HhSkillRepository extends JpaRepository<HhSkill, Long> {
    Optional<HhSkill> findBySkillName(String skillName);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO hh_skill (skill_name, count, skill_source)
        VALUES (:skill, :inc, :source)
        ON CONFLICT (skill_name, skill_source)
        DO UPDATE SET count = hh_skill.count + EXCLUDED.count
        """, nativeQuery = true)
    void upsertSkill(@Param("skill") String skill,
                     @Param("inc") long inc,
                     @Param("source") String source);

    List<HhSkill> findAllBySkillSource(SkillSource skillSource);

    Optional<HhSkill> findBySkillNameIgnoreCaseAndSkillSource(String skillName, SkillSource skillSource);

    // Сводка по навыкам с разбиением на AI/API и total (JPQL, без native)
    @Query("""
           select new com.kuklin.manageapp.bots.hhparserbot.models.SkillSummaryRow(
               lower(h.skillName),
               sum(case when h.skillSource = :ai  then h.count else 0 end),
               sum(case when h.skillSource = :api then h.count else 0 end),
               sum(h.count)
           )
           from HhSkill h
           group by lower(h.skillName)
           having sum(h.count) >= :minTotal
           order by sum(h.count) desc
           """)
    List<SkillSummaryRow> summary(@Param("ai")  SkillSource ai,
                                  @Param("api") SkillSource api,
                                  @Param("minTotal") long minTotal,
                                  Pageable pageable);
}
