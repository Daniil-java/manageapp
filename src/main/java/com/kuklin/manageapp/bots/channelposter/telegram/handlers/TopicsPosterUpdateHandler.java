package com.kuklin.manageapp.bots.channelposter.telegram.handlers;

import com.kuklin.manageapp.bots.channelposter.entities.RubricTopic;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.services.RubricTopicService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * /topics — банк тем рубрик:
 * /topics                    сколько тем в каждой рубрике
 * /topics myth|fact          новые темы рубрики (ещё без поста)
 * /topics add myth|fact ТЕМА своя тема
 * /topics off|on ID          выключить тему / вернуть выключенную
 * /topics fill myth|fact     пополнить банк через AI сейчас
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TopicsPosterUpdateHandler implements ChannelPosterUpdateHandler {

    // запас до лимита сообщения Telegram (4096)
    private static final int MESSAGE_LIMIT = 3800;

    private static final String HELP = """
            <b>Банк тем рубрик</b>
            /topics — сводка
            /topics myth · /topics fact — новые темы
            /topics add myth <i>тема</i> — своя тема
            /topics off <i>id</i> · /topics on <i>id</i> — выключить / вернуть
            /topics fill myth — пополнить через AI сейчас""";

    private final ChannelPosterTelegramBot bot;
    private final RubricTopicService topicService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasMessage() || update.getMessage().getText() == null) return;
        Long chatId = update.getMessage().getChatId();
        String[] parts = update.getMessage().getText().trim().split("\\s+", 4);
        String action = parts.length > 1 ? parts[1].toLowerCase() : "summary";

        try {
            switch (action) {
                case "summary" -> bot.sendReturnedMessage(chatId, buildSummary());
                case "add" -> add(chatId, parts);
                case "on", "off" -> toggle(chatId, parts, action.equals("on"));
                case "fill" -> fill(chatId, parts);
                default -> {
                    Optional<TopicCategory> rubric = findRubric(action);
                    if (rubric.isPresent()) {
                        sendList(chatId, rubric.get());
                    } else {
                        bot.sendReturnedMessage(chatId, HELP);
                    }
                }
            }
        } catch (Exception e) {
            log.error("/topics failed: {}", update.getMessage().getText(), e);
            bot.sendReturnedMessage(chatId, "❌ Ошибка: " + escape(e.getMessage()));
        }
    }

    private String buildSummary() {
        StringBuilder sb = new StringBuilder("<b>Банк тем рубрик</b>\n\n");
        for (TopicCategory rubric : topicService.getRubrics()) {
            Map<RubricTopic.Status, Long> counts = topicService.countByStatus(rubric.getId());
            long fresh = counts.get(RubricTopic.Status.NEW);
            sb.append(fresh < RubricTopicService.MIN_NEW_TOPICS ? "⚠️ " : "✅ ")
                    .append("<b>").append(escape(rubric.getName())).append("</b> (")
                    .append(getAlias(rubric)).append("): новых ").append(fresh)
                    .append(" · с постом ").append(counts.get(RubricTopic.Status.USED))
                    .append(" · выключено ").append(counts.get(RubricTopic.Status.DISABLED)).append('\n');
        }
        sb.append("\nНовых меньше ").append(RubricTopicService.MIN_NEW_TOPICS)
                .append(" — банк пополнится сам перед следующим постом.\n\n").append(HELP);
        return sb.toString();
    }

    private void sendList(Long chatId, TopicCategory rubric) {
        List<RubricTopic> topics = topicService.getNew(rubric.getId());
        StringBuilder sb = new StringBuilder("<b>").append(escape(rubric.getName())).append("</b> — новых тем: ")
                .append(topics.size()).append('\n');
        for (RubricTopic topic : topics) {
            String line = "\n#" + topic.getId() + " <i>" + escape(topic.getSection()) + "</i> · " + escape(topic.getText());
            if (sb.length() + line.length() > MESSAGE_LIMIT) {
                bot.sendReturnedMessage(chatId, sb.toString());
                sb.setLength(0);
            }
            sb.append(line);
        }
        sb.append("\n\n/topics off <i>id</i> — выключить тему");
        bot.sendReturnedMessage(chatId, sb.toString());
    }

    private void add(Long chatId, String[] parts) {
        Optional<TopicCategory> rubric = parts.length > 2 ? findRubric(parts[2].toLowerCase()) : Optional.empty();
        if (rubric.isEmpty() || parts.length < 4 || parts[3].isBlank()) {
            bot.sendReturnedMessage(chatId, "Формат: /topics add myth <i>тема</i>");
            return;
        }
        RubricTopic topic = topicService.add(rubric.get().getId(), parts[3]);
        bot.sendReturnedMessage(chatId, topic == null
                ? "Такая тема уже есть."
                : "✅ Тема #" + topic.getId() + " добавлена в «" + escape(rubric.get().getName()) + "».");
    }

    private void toggle(Long chatId, String[] parts, boolean enabled) {
        Long id = parts.length > 2 ? parseIdOrNull(parts[2]) : null;
        RubricTopic topic = id == null ? null : topicService.setEnabled(id, enabled);
        if (topic == null) {
            bot.sendReturnedMessage(chatId, "Нет темы с таким id.");
            return;
        }
        String state = switch (topic.getStatus()) {
            case NEW -> "в работе";
            case DISABLED -> "выключена";
            case USED -> "уже использована — пост по ней есть, не трогаю";
        };
        bot.sendReturnedMessage(chatId, "Тема #" + topic.getId() + " " + state + ": " + escape(topic.getText()));
    }

    private void fill(Long chatId, String[] parts) {
        Optional<TopicCategory> rubric = parts.length > 2 ? findRubric(parts[2].toLowerCase()) : Optional.empty();
        if (rubric.isEmpty()) {
            bot.sendReturnedMessage(chatId, "Формат: /topics fill myth");
            return;
        }
        bot.sendReturnedMessage(chatId, "📚 Пополняю банк «" + escape(rubric.get().getName()) + "»…");
        int added = topicService.fill(rubric.get());
        bot.sendReturnedMessage(chatId, added == 0
                ? "AI не дал новых тем — попробуй ещё раз позже."
                : "✅ Добавлено тем: " + added + ". Список: /topics " + getAlias(rubric.get()));
    }

    // myth → MYTH, fact → FACT
    private Optional<TopicCategory> findRubric(String alias) {
        return topicService.getRubrics().stream()
                .filter(c -> getAlias(c).equals(alias))
                .findFirst();
    }

    private static String getAlias(TopicCategory category) {
        return category.getTopicType().name().toLowerCase();
    }

    private static Long parseIdOrNull(String raw) {
        try {
            return Long.parseLong(raw.replace("#", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public String getHandlerListName() {
        return Command.POSTER_TOPICS.getCommandText();
    }
}
