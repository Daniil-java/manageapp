package com.kuklin.manageapp.bots.hhparserbot.services;

import com.kuklin.manageapp.bots.hhparserbot.entities.HhSkill;
import com.kuklin.manageapp.bots.hhparserbot.models.SkillSource;
import com.kuklin.manageapp.bots.hhparserbot.models.SkillSummaryRow;
import com.kuklin.manageapp.bots.hhparserbot.repositories.HhSkillRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class HhSkillService {
    private final HhSkillRepository hhSkillRepository;

    @Transactional
    public void saveSkills(List<String> keySkills, SkillSource skillSource) {
        Map<String, Long> delta = keySkills.stream()
                .map(s -> s == null ? null : s.trim())
                .filter(s -> s != null && !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        String src = skillSource.name();
        delta.forEach((skill, inc) -> hhSkillRepository.upsertSkill(skill, inc, src));
    }

    public List<HhSkill> findAllBySkillSource(SkillSource skillSource) {
        return hhSkillRepository.findAllBySkillSource(skillSource);
    }

    @Transactional(readOnly = true)
    public List<SkillSummaryRow> getSummary(long minTotal, int limit) {
        return hhSkillRepository.summary(SkillSource.AI, SkillSource.API, minTotal, PageRequest.of(0, limit));
    }

}
