package com.kuklin.manageapp.bots.channelposter.telegram.handlers;

import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.model.TopicCategoryNotFoundException;
import com.kuklin.manageapp.bots.channelposter.components.source.ArticleTextExtractor;
import com.kuklin.manageapp.bots.channelposter.components.source.TextUtils;
import com.kuklin.manageapp.bots.channelposter.services.PostQueueService;
import com.kuklin.manageapp.bots.channelposter.services.PostReviewService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.services.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class ArticlePosterUpdateHandler implements ChannelPosterUpdateHandler {
    private final TelegramService telegramService;
    private final ChannelPosterTelegramBot channelPosterTelegramBot;
    private final PostQueueService postQueueService;
    private final PostReviewService postReviewService;
    private final ArticleTextExtractor articleTextExtractor;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasMessage()) {
            processMessage(update);
        }
    }

    /**
     * Обработка входящего сообщения:
     * если это файл → парсим файл
     * если текст → отправляем как статью
     */
    private void processMessage(Update update) {
        if (update.getMessage().hasDocument()) {
            processDocument(update);
            return;
        }
        String article = update.getMessage().getText();
        // «/article текст» — команду из текста убираем
        if (article != null && article.startsWith(Command.POSTER_GET_ARTICLE.getCommandText())) {
            article = article.substring(Command.POSTER_GET_ARTICLE.getCommandText().length()).trim();
        }
        Long chatId = update.getMessage().getChatId();
        // одна ссылка — скачиваем статью сами, ссылка станет источником поста
        if (article != null && article.matches("https?://\\S+")) {
            String text = articleTextExtractor.extract(article);
            if (text == null) {
                channelPosterTelegramBot.sendReturnedMessage(chatId,
                        "Не получилось достать текст со страницы — пришли текст статьи или файл.");
                return;
            }
            sendPostQueue(chatId, text, article);
            return;
        }
        if (article == null || article.length() < 200) {
            channelPosterTelegramBot.sendReturnedMessage(chatId,
                    "Пришли ссылку на статью, её текст (от 200 символов) или файл .txt / .pdf / .html");
            return;
        }
        sendPostQueue(chatId, article, null);
    }

    /**
     * Обработка документа (.txt или .pdf):
     * скачиваем файл из Telegram
     * извлекаем текст
     * отправляем превью (первые 128 символов)
     * кладем в очередь постов
     */
    private void processDocument(Update update) {
        Long chatId = update.getMessage().getChatId();
        Document doc = update.getMessage().getDocument();
        String fileId = doc.getFileId();
        String filename = doc.getFileName().toLowerCase();

        // нормальная проверка
        if (!(filename.endsWith(".txt") || filename.endsWith(".pdf") || filename.endsWith(".html"))) {
            channelPosterTelegramBot.sendReturnedMessage(
                    chatId,
                    "Обрабатываются только документы .txt или .pdf"
            );
            return;
        }

        try {
            byte[] bytes = telegramService.downloadFileOrNull(channelPosterTelegramBot, fileId);
            if (bytes == null) {
                throw new RuntimeException("Не удалось скачать файл");
            }

            String text = null;
            if (filename.endsWith(".txt")) {
                text = new String(bytes, StandardCharsets.UTF_8);

            } else if (filename.endsWith(".pdf")) { // pdf
                try (PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes))) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    text = stripper.getText(document);
                }
            } else if (filename.endsWith(".html")) {
                // Декодируем байты в строку (обычно UTF-8)
                String html = new String(bytes, StandardCharsets.UTF_8);

                // Используем Jsoup для парсинга
                // .text() извлекает только чистый текст без тегов и скриптов
                text = org.jsoup.Jsoup.parse(html).text();
            }

            if (text == null) {
                channelPosterTelegramBot.sendReturnedMessage(update, "Не получилось извлечь данные");
                return;
            }
            channelPosterTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    TextUtils.truncate(text, 128)
            );

            sendPostQueue(chatId, text, null);
        } catch (Exception e) {
            channelPosterTelegramBot.sendReturnedMessage(
                    chatId,
                    "Ошибка при обработке файла 😢"
            );
        }
    }

    /**
     * Создание записи в очереди постов:
     * генерируем пост через сервис
     * отправляем пользователю результат + кнопки
     */
    private void sendPostQueue(Long chatId, String text, String sourceUrl) {
        channelPosterTelegramBot.sendReturnedMessage(chatId, "✍️ Пишу пост и рисую картинку…");
        try {
            PostQueue postQueue = postQueueService.createPostQueueByText(
                    text, TopicCategory.TopicType.ARTICLE
            );

            if (postQueue == null) {
                throw new TopicCategoryNotFoundException();
            }
            if (sourceUrl != null) {
                postQueue = postQueueService.save(postQueue
                        .setSourceUrl(sourceUrl)
                        .setSourceName(hostOrNull(sourceUrl)));
            }

            postReviewService.prepareAndSend(postQueue, List.of(chatId));

        } catch (TopicCategoryNotFoundException e) {
            channelPosterTelegramBot.sendReturnedMessage(
                    chatId,
                    "Не получилось сгенерировать текст!"
            );
        }
    }

    private static String hostOrNull(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? null : host.replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.POSTER_GET_ARTICLE.getCommandText();
    }
}
