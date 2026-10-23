package com.kuklin.manageapp.bots.channelposter.telegram.handlers;

import com.kuklin.manageapp.bots.channelposter.components.source.RedditSourceFetcher;
import com.kuklin.manageapp.bots.channelposter.components.source.SourceHttpClient;
import com.kuklin.manageapp.bots.channelposter.components.source.TextUtils;
import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import com.kuklin.manageapp.bots.channelposter.entities.source.ContentSource;
import com.kuklin.manageapp.bots.channelposter.entities.source.SourceItem;
import com.kuklin.manageapp.bots.channelposter.model.source.FetchedItem;
import com.kuklin.manageapp.bots.channelposter.services.ChannelAutopilotService;
import com.kuklin.manageapp.bots.channelposter.services.source.ContentPipeline;
import com.kuklin.manageapp.bots.channelposter.services.source.ContentSourceService;
import com.kuklin.manageapp.bots.channelposter.services.source.SourceFetchService;
import com.kuklin.manageapp.bots.channelposter.services.source.SourceItemService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * /src — источники канала:
 * /src                       список с состоянием и статистикой за 7 дней
 * /src add rss URL [имя]     RSS/Atom-лента (перед добавлением проверяем, что парсится)
 * /src add pubmed ЗАПРОС     поисковый запрос PubMed
 * /src add reddit ИМЯ        сабреддит
 * /src on|off|del ID         включить, выключить, удалить
 * /src run                   собрать, отфильтровать и подготовить посты прямо сейчас
 * /src report                утренний отчёт сейчас
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SourcesPosterUpdateHandler implements ChannelPosterUpdateHandler {

    private static final String HELP = """
            <b>Источники</b>
            /src — список
            /src add rss <i>ссылка на ленту</i> [имя]
            /src add pubmed <i>поисковый запрос PubMed</i>
            /src add reddit <i>сабреддит</i>
            /src on <i>id</i> · /src off <i>id</i> · /src del <i>id</i>
            /src run — собрать и подготовить посты сейчас
            /src report — отчёт о канале""";

    private final ChannelPosterTelegramBot bot;
    private final ChannelPosterBotKeyComponent keys;
    private final ContentSourceService sourceService;
    private final SourceItemService itemService;
    private final SourceFetchService fetchService;
    private final ContentPipeline pipeline;
    private final ChannelAutopilotService autopilotService;
    private final ChannelPosterProperties properties;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasMessage() || update.getMessage().getText() == null) return;
        Long chatId = update.getMessage().getChatId();
        String[] parts = update.getMessage().getText().trim().split("\\s+", 4);
        String action = parts.length > 1 ? parts[1].toLowerCase() : "list";

        try {
            switch (action) {
                case "list" -> bot.sendReturnedMessage(chatId, buildList());
                case "add" -> add(chatId, parts);
                case "on", "off" -> toggle(chatId, parts, action.equals("on"));
                case "del" -> delete(chatId, parts);
                case "run" -> run(chatId);
                case "report" -> bot.sendReturnedMessage(chatId, autopilotService.buildReport());
                default -> bot.sendReturnedMessage(chatId, HELP);
            }
        } catch (Exception e) {
            log.error("/src failed: {}", update.getMessage().getText(), e);
            bot.sendReturnedMessage(chatId, "❌ Ошибка: " + escape(SourceHttpClient.describe(e)));
        }
    }

    private String buildList() {
        List<ContentSource> sources = sourceService.getAll();
        if (sources.isEmpty()) {
            return "Источников нет.\n\n" + HELP;
        }
        Map<Long, Map<SourceItem.Status, Long>> stats =
                itemService.statsBySource(Instant.now().minus(Duration.ofDays(7)));

        StringBuilder sb = new StringBuilder("<b>Источники</b> (за 7 дней: найдено / одобрено AI)\n\n");
        for (ContentSource s : sources) {
            Map<SourceItem.Status, Long> st = stats.getOrDefault(s.getId(), Map.of());
            long total = st.values().stream().mapToLong(Long::longValue).sum();
            long approved = st.getOrDefault(SourceItem.Status.APPROVED, 0L)
                    + st.getOrDefault(SourceItem.Status.PROCESSED, 0L);
            String state = !Boolean.TRUE.equals(s.getActive()) ? "⏸"
                    : (s.getFailCount() != null && s.getFailCount() > 0) ? "⚠️" : "✅";

            sb.append(state).append(" <b>#").append(s.getId()).append("</b> ").append(escape(s.getName()))
                    .append(" <i>").append(s.getType()).append("</i> — ").append(total).append(" / ").append(approved);
            if (s.getFailCount() != null && s.getFailCount() > 0) {
                sb.append("\n      ").append(escape(s.getLastError()));
            }
            sb.append('\n');
        }
        if (!keys.hasRedditCredentials() && sources.stream()
                .anyMatch(s -> s.getType() == ContentSource.SourceType.REDDIT && Boolean.TRUE.equals(s.getActive()))) {
            sb.append("\nℹ️ Для Reddit нужны ключи API: CHANNELPOSTER_REDDIT_CLIENT_ID / _SECRET.");
        }
        sb.append("\n/src help — команды");
        return sb.toString();
    }

    private void add(Long chatId, String[] parts) throws Exception {
        if (parts.length < 4) {
            bot.sendReturnedMessage(chatId, HELP);
            return;
        }
        String typeRaw = parts[2].toLowerCase();
        String rest = parts[3].trim();

        ContentSource.SourceType type;
        String address;
        String name;
        int maxItems;
        switch (typeRaw) {
            case "rss" -> {
                type = ContentSource.SourceType.RSS;
                String[] urlAndName = rest.split("\\s+", 2);
                address = urlAndName[0];
                name = urlAndName.length > 1 ? urlAndName[1] : URI.create(address).getHost().replaceFirst("^www\\.", "");
                maxItems = 15;
            }
            case "pubmed" -> {
                type = ContentSource.SourceType.PUBMED;
                address = rest;
                name = "PubMed · " + TextUtils.truncate(rest, 40);
                maxItems = 10;
            }
            case "reddit", "sub" -> {
                type = ContentSource.SourceType.REDDIT;
                address = RedditSourceFetcher.normalizeSubreddit(rest);
                if (address == null) {
                    bot.sendReturnedMessage(chatId, "Не понял сабреддит: " + escape(rest));
                    return;
                }
                name = "r/" + address;
                maxItems = 15;
            }
            default -> {
                bot.sendReturnedMessage(chatId, "Тип источника: rss, pubmed или reddit.");
                return;
            }
        }

        if (sourceService.exists(type, address)) {
            bot.sendReturnedMessage(chatId, "Такой источник уже есть — /src");
            return;
        }

        // пробный сбор: не добавляем то, что не парсится
        ContentSource probe = new ContentSource().setType(type).setName(name).setAddress(address).setMaxItems(maxItems);
        List<FetchedItem> items;
        try {
            items = fetchService.preview(probe);
        } catch (Exception e) {
            boolean redditWithoutKeys = type == ContentSource.SourceType.REDDIT && !keys.hasRedditCredentials();
            bot.sendReturnedMessage(chatId, "❌ Источник не отвечает: " + escape(SourceHttpClient.describe(e))
                    + (redditWithoutKeys ? "\nДля Reddit нужны ключи API (CHANNELPOSTER_REDDIT_CLIENT_ID / _SECRET)." : ""));
            return;
        }
        if (items.isEmpty() && type == ContentSource.SourceType.RSS) {
            bot.sendReturnedMessage(chatId, "❌ Лента пустая — проверь ссылку.");
            return;
        }

        ContentSource saved = sourceService.create(type, name, address, maxItems);
        StringBuilder sb = new StringBuilder("✅ Добавлен #").append(saved.getId()).append(' ').append(escape(name))
                .append("\nСейчас там ").append(items.size()).append(" материалов");
        items.stream().limit(3).forEach(i -> sb.append("\n• ").append(escape(TextUtils.truncate(i.getTitle(), 100))));
        bot.sendReturnedMessage(chatId, sb.toString());
    }

    private void toggle(Long chatId, String[] parts, boolean active) {
        ContentSource source = findOrReply(chatId, parts);
        if (source == null) return;
        sourceService.setActive(source, active);
        bot.sendReturnedMessage(chatId, (active ? "▶️ Включён" : "⏸ Выключен") + " #" + source.getId() + " " + escape(source.getName()));
    }

    private void delete(Long chatId, String[] parts) {
        ContentSource source = findOrReply(chatId, parts);
        if (source == null) return;
        sourceService.delete(source);
        bot.sendReturnedMessage(chatId, "🗑 Удалён #" + source.getId() + " " + escape(source.getName()));
    }

    private ContentSource findOrReply(Long chatId, String[] parts) {
        if (parts.length < 3) {
            bot.sendReturnedMessage(chatId, "Укажи id источника: /src off 3");
            return null;
        }
        try {
            ContentSource source = sourceService.findById(Long.parseLong(parts[2])).orElse(null);
            if (source == null) {
                bot.sendReturnedMessage(chatId, "Нет источника #" + parts[2]);
            }
            return source;
        } catch (NumberFormatException e) {
            bot.sendReturnedMessage(chatId, "id — это число: /src off 3");
            return null;
        }
    }

    private void run(Long chatId) {
        if (pipeline.isBusy()) {
            bot.sendReturnedMessage(chatId, "⏳ Конвейер уже работает — подожди пару минут.");
            return;
        }
        bot.sendReturnedMessage(chatId, "⏳ Собираю источники, фильтрую и готовлю посты — это займёт несколько минут.");
        // не держим поток обработки апдейтов, пока идёт сбор и генерация
        CompletableFuture.runAsync(() -> {
            try {
                ContentPipeline.RunResult result = pipeline.runAll();
                if (result == null) {
                    bot.sendReturnedMessage(chatId, "⏳ Конвейер уже работает — подожди пару минут.");
                    return;
                }
                bot.sendReturnedMessage(chatId, "Готово: новых материалов " + result.fetched()
                        + ", одобрено AI " + result.approved()
                        + ", постов на проверку " + result.posts()
                        + (result.posts() == 0 ? "\n(в работе уже " + properties.getPipelineTarget() + " постов или нечего предложить)" : ""));
            } catch (Exception e) {
                log.error("/src run failed", e);
                bot.sendReturnedMessage(chatId, "❌ Конвейер упал: " + escape(SourceHttpClient.describe(e)));
            }
        });
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public String getHandlerListName() {
        return Command.POSTER_SOURCES.getCommandText();
    }
}
