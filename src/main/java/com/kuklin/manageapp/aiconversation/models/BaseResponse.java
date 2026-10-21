package com.kuklin.manageapp.aiconversation.models;


public abstract class BaseResponse {
    protected abstract String getContent();

    public AiResponse toAiResponse() {

        return new AiResponse().setContent(getContent());
    }
}
