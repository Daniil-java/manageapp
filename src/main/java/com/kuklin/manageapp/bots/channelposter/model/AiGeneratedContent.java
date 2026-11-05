package com.kuklin.manageapp.bots.channelposter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiGeneratedContent {

    @JsonProperty("post_title")
    private String postTitle;
    @JsonProperty("post_text")
    private String postText;
    @JsonProperty("image_prompt")
    private String imagePrompt;
    @JsonProperty("image_query")
    private String imageQuery;
    @JsonProperty("image_query_broad")
    private String imageQueryBroad;
}
