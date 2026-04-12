package com.kuklin.manageapp.bots.channelposter.telegram.handlers;

import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.model.TopicCategoryNotFoundException;
import com.kuklin.manageapp.bots.channelposter.services.PostQueueService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import com.kuklin.manageapp.common.services.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
@Component
@Slf4j
public class ArticlePosterUpdateHandler implements ChannelPosterUpdateHandler {
    // команды для callback-кнопок
    private static final String APPROVE_CMD = "APPROVE";
    private static final String REJECT_CMD = "REJECT";
    private static final String SHORT_CMD = "SHORT";
    // следующая стадия пайплайна (переход к обработке изображений)
    private static final Command nextHandlerCommand = Command.POSTER_IMAGE;

    private final TelegramService telegramService;
    private final ChannelPosterTelegramBot channelPosterTelegramBot;
    private final PostQueueService postQueueService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasMessage()) {
            processMessage(update);
        } else if (update.hasCallbackQuery()) {
            processCallback(update);
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
        sendPostQueue(update.getMessage().getChatId(), article);
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
                    text.substring(0, 128)
            );

            sendPostQueue(chatId, text);
        } catch (Exception e) {
            channelPosterTelegramBot.sendReturnedMessage(
                    chatId,
                    "Ошибка при обработке файла 😢"
            );
        }
    }

    /**
     * Генерация inline-клавиатуры:
     * APPROVE → отправить дальше в пайплайн
     * REJECT → отклонить
     */
    public static InlineKeyboardMarkup getGeneratedTextKeyboard(Long postQueueId) {
        //аппрув, перегенерация, удалить
        TelegramKeyboard.TelegramKeyboardBuilder builder =
                new TelegramKeyboard.TelegramKeyboardBuilder();

        builder
                .row(
                        TelegramKeyboard.button("✅APPROVE", nextHandlerCommand.getCommandText() + TelegramBot.DEFAULT_DELIMETER + ImagePosterUpdateHandler.APPROVE_CMD + TelegramBot.DEFAULT_DELIMETER + postQueueId),
                        TelegramKeyboard.button("❌REJECT", nextHandlerCommand.getCommandText() + TelegramBot.DEFAULT_DELIMETER + ImagePosterUpdateHandler.REJECT_CMD + TelegramBot.DEFAULT_DELIMETER + postQueueId)
                ).row(
                        TelegramKeyboard.button("КОРОЧЕ", Command.POSTER_GET_ARTICLE.getCommandText() + TelegramBot.DEFAULT_DELIMETER + SHORT_CMD + TelegramBot.DEFAULT_DELIMETER + postQueueId)
                )
        ;

        return builder.build();
    }

    /**
     * Обработка callback-кнопок:
     * APPROVE → повторно отправляем текст в очередь (следующий шаг)
     * REJECT → просто уведомление
     */
    private void processCallback(Update update) {
        String data = update.getCallbackQuery().getData();
        String text = update.getCallbackQuery().getMessage().getText();

        try {
            String cmd = data.split(TelegramBot.DEFAULT_DELIMETER)[1];
            if (cmd.equals(APPROVE_CMD)) {
                channelPosterTelegramBot.sendReturnedMessage(
                        update.getCallbackQuery().getMessage().getChatId(),
                        "Принято"
                );

                sendPostQueue(update.getCallbackQuery().getMessage().getChatId(), text);
            } else if (cmd.equals(REJECT_CMD)) {
                channelPosterTelegramBot.sendReturnedMessage(
                        update.getCallbackQuery().getMessage().getChatId(),
                        "Отклонено"
                );
            } else if (cmd.equals(SHORT_CMD)) {
                Long postId = Long.parseLong(data.split(TelegramBot.DEFAULT_DELIMETER)[2]);
                PostQueue postQueue = postQueueService.makePostQueueContentShorter(postId);
                channelPosterTelegramBot.sendReturnedMessage(
                        update.getCallbackQuery().getMessage().getChatId(),
                        postQueue.getTextContent(),
                        getGeneratedTextKeyboard(postQueue.getId()),
                        null
                );
            }
        } catch (Exception e) {
            channelPosterTelegramBot.sendReturnedMessage(
                    update.getCallbackQuery().getMessage().getChatId(),
                    "Ошибка!"
            );
        }
    }

    /**
     * Создание записи в очереди постов:
     * генерируем пост через сервис
     * отправляем пользователю результат + кнопки
     */
    private void sendPostQueue(Long chatId, String text) {
        try {
            PostQueue postQueue = postQueueService.createPostQueueByText(
                    text, TopicCategory.TopicType.ARTICLE
            );

            if (postQueue == null) {
                throw new TopicCategoryNotFoundException();
            }

            channelPosterTelegramBot.sendReturnedMessage(
                    chatId,
                    postQueue.getTextContent(),
                    getGeneratedTextKeyboard(postQueue.getId()),
                    null
            );

        } catch (TopicCategoryNotFoundException e) {
            channelPosterTelegramBot.sendReturnedMessage(
                    chatId,
                    "Не получилось сгенерировать текст!"
            );
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.POSTER_GET_ARTICLE.getCommandText();
    }
}
