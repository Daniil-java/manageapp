package com.kuklin.manageapp.bots.hhparserbot.models;

import lombok.Data;

import java.util.List;

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

