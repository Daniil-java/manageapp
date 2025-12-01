package com.kuklin.manageapp.bots.hhparserbot.controllers;

import com.kuklin.manageapp.bots.hhparserbot.entities.HhSkill;
import com.kuklin.manageapp.bots.hhparserbot.models.SkillSource;
import com.kuklin.manageapp.bots.hhparserbot.models.SkillSummaryRow;
import com.kuklin.manageapp.bots.hhparserbot.services.HhSkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hhbot/skills")
@RequiredArgsConstructor
@Slf4j
public class HhSkillController {
    private final HhSkillService hhSkillService;

    @ResponseBody
    @GetMapping(value = "/data", produces = "application/json")
    public List<HhSkill> findAllBySkillSource(@RequestParam SkillSource skillSource) {
        return hhSkillService.findAllBySkillSource(skillSource);
    }

    @GetMapping(value = "/stats/summary", produces = "application/json")
    public List<SkillSummaryRow> summary(@RequestParam(name = "minTotal", defaultValue = "3") long minTotal,
                                         @RequestParam(name = "limit",    defaultValue = "50") int limit) {
        log.info("HH BOT: skills metrics controller request: " + minTotal + " " + limit);
        return hhSkillService.getSummary(minTotal, limit);
    }
}
