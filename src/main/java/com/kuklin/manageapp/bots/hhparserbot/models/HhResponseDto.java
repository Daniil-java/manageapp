package com.kuklin.manageapp.bots.hhparserbot.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
public class HhResponseDto {

    private String alternateUrl;
    private String brandedDescription;
    private Contacts contacts;
    private Department department;
    private String description;
    private Employment employment;
    private Experience experience;
    private String id;
    private List<String> keySkills;
    private String name;
    private String previousId;
    private String publishedAt;
    private boolean responseLetterRequired;
    private String responseUrl;
    private Salary salary;
    private Schedule schedule;
    private HhEmployerDto employer;

    public List<String> getKeySkills() {
        if (keySkillsItems == null) return Collections.emptyList();
        return keySkillsItems.stream()
                .map(KeySkillItem::getName)      // берём name
                .filter(Objects::nonNull)         // пропускаем пустые {}
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonProperty("key_skills")
    private List<KeySkillItem> keySkillsItems;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KeySkillItem {
        private String name;
    }

    @Data
    public static class Contacts {
        private String email;
        private String name;
        private List<Object> phones;
    }
    @Data
    public static class Department {
        private String id;
        private String name;
    }
    @Data
    public static class Employment {
        private String id;
        private String name;
    }
    @Data
    public static class Experience {
        private String id;
        private String name;
    }
    @Data
    public static class Salary {
        private String currency;
        private Integer from;
        private boolean gross;
        private Integer to;
    }
    @Data
    public static class Schedule {
        private String id;
        private String name;
    }
}

