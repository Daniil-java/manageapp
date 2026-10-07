package com.kuklin.manageapp.bots.hhparserbot.entities;

import com.kuklin.manageapp.bots.hhparserbot.models.SkillSource;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "hh_skill")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class HhSkill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String skillName;
    private Long count;
    @Enumerated(EnumType.STRING)
    private SkillSource skillSource;
}
