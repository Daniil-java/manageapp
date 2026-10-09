package com.kuklin.manageapp.bots.channelposter.components;

import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.model.AiGeneratedContent;
import com.kuklin.manageapp.bots.channelposter.model.TopicCategoryNotFoundException;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public interface ContentGenerator {

    // Генерирует текст поста из сырого текста статьи.
    // Сохраняет PostQueue со статусом TEXT_GENERATED.
    // Возвращает сохранённый пост — чтобы вызывающий код знал id.
    AiGeneratedContent generateText(String article) throws TopicCategoryNotFoundException;

    // Генерирует картинку для уже готового текста поста.
    // Сохраняет PostImage, обновляет статус PostQueue → IMAGE_GENERATED.
    byte[] generateImage(PostQueue post) throws IOException;

    // Какой тип поста умеет обрабатывать эта реализация.
    TopicCategory.TopicType supportedType();
}
