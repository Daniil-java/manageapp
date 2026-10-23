package com.kuklin.manageapp.bots.channelposter.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.channelposter.components.source.ArticleTextExtractor;
import com.kuklin.manageapp.bots.channelposter.components.source.TextUtils;
import com.kuklin.manageapp.bots.channelposter.entities.PostImage;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.model.AiGeneratedContent;
import com.kuklin.manageapp.bots.channelposter.services.PostImageService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

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
                    Ты — экспертный контент-мейкер и приветливый популяризатор науки. Твоя задача: прочитать материал и подготовить два элемента: пост для Telegram и промпт для генерации изображения.
                    Материал может быть научной новостью, статьёй из блога, абстрактом исследования из PubMed или постом с Reddit. Материал может быть на английском — пост всегда пиши на русском.

                    """ + PostPromptParts.JSON_FORMAT + """

                    ---
                    ИНСТРУКЦИЯ ДЛЯ "post_text":
                    Роль: Приветливый и эрудированный приятель, который делится свежими новостями науки, диетологии или интересными находками. Никакого тона "наставника", не учи читателя жизни и избегай морализаторства. Будь открытым, позитивным и увлекательным.

                    Формат и объем (для эпохи быстрых медиа):
                    - Технический лимит 4096 символов, НО идеальный объем для удержания внимания — около 1000-1500 символов.
                    - Текст должен быть динамичным. Разбивай его на короткие абзацы (по 2-3 предложения). Читатель не должен уставать, текст должен читаться легко и на одном дыхании.

                    """ + PostPromptParts.MARKUP_RULES + """
                    - Ссылку на источник НЕ пиши — бот добавит её сам.

                    Структура:
                    - Яркий заголовок-крючок (выдели его тегом <b>).
                    - Проблема: почему это интересно и касается лично читателя?
                    - Авторитетность и суть: что конкретно выяснили (объясняй просто). ОБЯЗАТЕЛЬНО ссылайся на авторитетные институты, университеты, журналы или имена исследователей, если они упомянуты в исходнике (например: «Ученые из Гарварда выяснили...»).
                    - Честность: если это исследование на животных или клетках, наблюдательное исследование (связь, а не причина) или маленькая выборка — коротко скажи об этом. Не преувеличивай выводы.
                    - Вывод: как это применить. Подавай это как классную идею на заметку, а не как строгое указание.

                    """ + PostPromptParts.STYLE_RULES + """

                    ---
                    """ + PostPromptParts.IMAGE_RULES + """

                    """ + PostPromptParts.JSON_ONLY + """
                    Текст статьи для обработки:
                    %s
                    """;

    @Override
    public AiGeneratedContent generateText(String article) {
        // 1. Получаем "сырой" ответ от провайдера
        // Используем .replace вместо String.format во избежание ошибок с символом %
        String rawResponse = openAiProviderProcessor.fetchJsonResponse(
                component.getAiKey(),
                TEXT_PROMPT.replace("%s", TextUtils.truncate(article, ArticleTextExtractor.MAX_CHARS)),
                BotIdentifier.CHANNEL_POSTER,
                "article content generator",
                MetricsAiInteractionRecord.AiMessageType.TEXT
        );

        try {
            // 2. Очищаем строку от возможных markdown-тегов ```json ... ```
            if (rawResponse == null) {
                return null;
            }
            String json = cleanJson(rawResponse.trim());
            // 3. Парсим JSON в наш DTO
            return objectMapper.readValue(json, AiGeneratedContent.class);
        } catch (Exception e) {
            log.error("Error parsing AI JSON response: {}", e.getMessage());
            // Если JSON сломался, возвращаем сырой текст как fallback (или кидаем exception)
            return null;
        }
    }

    static String cleanJson(String json) {
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
    public byte[] generateImage(PostQueue post) throws IOException {
        postImageService.deletePostImageByPostQueueId(post.getId());

        try {
            return openAiProviderProcessor.generateImageBytes(
                    component.getAiKey(),
                    post.getImageDescription(),
                    BotIdentifier.CHANNEL_POSTER,
                    "img content generator"
            );
        } catch (Exception e) {
            log.error("Image generating error!", e);
            return null;
        }

//        PostImage postImage = postImageService.saveNewImage(
//                PostImage.ImageSource.AI_GENERATED,
//                PostImage.ImageStatus.READY,
//                chatId,
//                messageId,
//                post.getId()
//        );
//        String fileName = supportedType().name() + UUID.randomUUID() + ".png";
//        String path = FilesUtils.saveImage(bytes, fileName, IMG_DIR);
    }

    public class GeneratedImageResult {

        private final byte[] bytes;
        private final PostImage postImage;

        public GeneratedImageResult(byte[] bytes, PostImage postImage) {
            this.bytes = bytes;
            this.postImage = postImage;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public PostImage getPostImage() {
            return postImage;
        }
    }

    @Override
    public TopicCategory.TopicType supportedType() {
        return TopicCategory.TopicType.ARTICLE;
    }
}
