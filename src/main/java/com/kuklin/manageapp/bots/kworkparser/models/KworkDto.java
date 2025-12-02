package com.kuklin.manageapp.bots.kworkparser.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Accessors(chain = true)
public class KworkDto {
    private Long id;
    private String status;
    private String name;
    private String description;

    @JsonProperty("category_id")
    private String categoryId;

    private String lang;

    private String priceLimit;

    @JsonProperty("max_days")
    private String maxDays;

    @JsonProperty("date_create")
    private String dateCreate;
    @JsonProperty("date_active")
    private String dateActive;
    @JsonProperty("date_expire")
    private String dateExpire;

    private WantDates wantDates;
    private User user;

    // Иногда встречаются доп. поля:
    private List<Object> files;
    @JsonProperty("kwork_count")
    private Integer kworkCount;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    public static class WantDates {
        private String dateCreate;
        private String dateActive;
        private String dateExpire;
        private String dateReject;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    public static class User {
        @JsonProperty("USERID")
        private Long userId;
        private String username;
        private String profilepicture;
        private Map<String, Object> data;
    }
}
