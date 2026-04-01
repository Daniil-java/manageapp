package com.kuklin.manageapp.bots.channelposter.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.channelposter.entities.PostImage;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.model.AiGeneratedContent;
import com.kuklin.manageapp.bots.channelposter.model.TopicCategoryNotFoundException;
import com.kuklin.manageapp.bots.channelposter.services.PostImageService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.utils.FilesUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArticleContentGenerator implements ContentGenerator {
    private final OpenAiProviderProcessor openAiProviderProcessor;
    private final PostImageService postImageService;
    private final ChannelPosterBotKeyComponent component;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TEXT_PROMPT =
            """
                    Ты — экспертный контент-мейкер и приветливый популяризатор науки. Твоя задача: прочитать научную статью и подготовить два элемента: пост для Telegram и промпт для генерации изображения.
                                        
                    ВЫХОДНЫЕ ДАННЫЕ ДОЛЖНЫ БЫТЬ СТРОГО В ФОРМАТЕ JSON с двумя полями:
                    1. "post_text": текст поста.
                    2. "image_prompt": финальный текст промпта для DALL-E 3 (Максимум 3900 символов).
                                        
                    ---
                    ИНСТРУКЦИЯ ДЛЯ "post_text":
                    Роль: Приветливый и эрудированный приятель, который делится свежими новостями науки, диетологии или интересными находками. Никакого тона "наставника", не учи читателя жизни и избегай морализаторства. Будь открытым, позитивным и увлекательным.
                                        
                    Формат и объем (для эпохи быстрых медиа):
                    - Технический лимит 4096 символов, НО идеальный объем для удержания внимания — около 1000-1500 символов.
                    - Текст должен быть динамичным. Разбивай его на короткие абзацы (по 2-3 предложения). Читатель не должен уставать, текст должен читаться легко и на одном дыхании.
                                        
                    РАЗМЕТКА ТЕЛЕГРАМ (HTML):
                    - Используй ТОЛЬКО разрешенные теги: <b>жирный</b>, <i>курсив</i>, <u>подчеркивание</u>, <s>зачеркивание</s>.
                    - КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО ИСПОЛЬЗОВАТЬ ТЕГИ: <br>, <p>, <div>. Телеграм их не поддерживает.
                    - ДЛЯ ПЕРЕНОСА СТРОКИ: Просто используй стандартный перенос строки (клавиша Enter/символ новой строки).
                                        
                    Структура:
                    - Яркий заголовок-крючок (выдели его тегом <b>).
                    - Проблема: почему это интересно и касается лично читателя?
                    - Авторитетность и суть: что конкретно выяснили (объясняй просто). ОБЯЗАТЕЛЬНО ссылайся на авторитетные институты, университеты или имена исследователей, если они упомянуты в исходнике (например: «Ученые из Гарварда выяснили...»).
                    - Вывод: как это применить. Подавай это как классную идею на заметку, а не как строгое указание.
                                        
                    Правила стиля:
                    - ЗАПРЕЩЕННЫЕ СЛОВА: «В современном мире», «Важно отметить», «Ключевой аспект», «Инновационный», «Таким образом», «В заключение», «Погрузимся в мир...», «инсайт», «открытие», «совет», «секрет», «лайфхак».
                    - Никакого пассивного залога. Минимум эмодзи (1-3 на весь пост).
                    - Аналогии: сложные термины объясняй через понятный быт (уборка, ключи и т.д.).
                    - Финал: только твердое утверждение. Не задавай вопросов аудитории.
                                        
                    ---
                    ИНСТРУКЦИЯ ДЛЯ "image_prompt":
                    Создай промпт на основе статьи, заполнив этот шаблон:
                    "[ПРИДУМАННЫЙ ТОБОЙ СЮЖЕТ]. Высокореалистичная лайфстайл-фотография, снятая на профессиональную камеру. Естественное, мягкое дневное освещение. Минималистичный, чистый фон в светлых тонах (например, белые стены, светлое дерево, просторное помещение). Визуальный стиль: легкость, свежесть, здоровье. Доминируют пастельные оттенки. Акцентный цвет: теплый зеленый или мятный. Полное отсутствие неона, киберпанка, футуристичных элементов, фальшивых инфографических окон, плавающих иконок, графиков или цифровых оверлеев. Камера на уровне глаз. Естественная глубина резкости, мягко размывающая фон. Люди на фото имеют реалистичное телосложение и живые выражения лиц. В нижней части изображения, на чистой полосе акцентного зеленого цвета, аккуратно и четко напечатан заголовок на русском языке: «[ПРИДУМАННЫЙ ТОБОЙ ЗАГОЛОВОК ДО 5 СЛОВ]». Шрифт простой, белый, легко читаемый. Композиция сбалансированная и подлинная."
                                        
                    ВЕРНИ ТОЛЬКО JSON, БЕЗ ЛИШНЕГО ТЕКСТА, ОБРАМЛЕНИЙ ИЛИ КОММЕНТАРИЕВ.!!!
                    Текст статьи для обработки:
                    %s
                    """;

    @Override
    public AiGeneratedContent generateText(String article) {
        // 1. Получаем "сырой" ответ от провайдера
        // Используем .replace вместо String.format во избежание ошибок с символом %
        String rawResponse = openAiProviderProcessor.fetchResponse(
                component.getAiKey(),
                TEXT_PROMPT.replace("%s", article),
                BotIdentifier.CHANNEL_POSTER,
                "article content generator",
                MetricsAiInteractionRecord.AiMessageType.TEXT
        );

        try {
            // 2. Очищаем строку от возможных markdown-тегов ```json ... ```
            String json = cleanJson(rawResponse);
            // 3. Парсим JSON в наш DTO
            return objectMapper.readValue(json, AiGeneratedContent.class);
        } catch (Exception e) {
            log.error("Error parsing AI JSON response: {}", e.getMessage());
            // Если JSON сломался, возвращаем сырой текст как fallback (или кидаем exception)
            return null;
        }
    }

    private String cleanJson(String json) {
        if (json.startsWith("```")) {
            json = json.trim();

            if (json.startsWith("```json")) {
                json = json.substring(7); // длина "```json"
            } else if (json.startsWith("```")) {
                json = json.substring(3); // просто "```"
            }

            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3);
            }

            json = json.trim();
        }
        return json;
    }

    @Override
    public PostImage generateImage(PostQueue post) throws IOException {
        postImageService.deletePostImageByPostQueueId(post.getId());
        byte[] bytes = null;
        try {
            bytes = openAiProviderProcessor.generateImageBytes(
                    component.getAiKey(),
                    post.getImageDescription(),
                    BotIdentifier.CHANNEL_POSTER,
                    "img content generator"
            );
        } catch (Exception e) {
            log.error("Image generating error!");
            return null;
        }


        String fileName = supportedType().name() + UUID.randomUUID() + ".png";

        String path = FilesUtils.saveImage(bytes, fileName, "poster/article");
        return postImageService.saveNewImage(
                PostImage.ImageSource.AI_GENERATED,
                PostImage.ImageStatus.READY,
                path,
                post.getId()
        );
    }

    @Override
    public TopicCategory.TopicType supportedType() {
        return TopicCategory.TopicType.ARTICLE;
    }
}
