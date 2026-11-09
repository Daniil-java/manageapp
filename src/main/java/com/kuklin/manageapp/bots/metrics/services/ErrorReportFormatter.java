package com.kuklin.manageapp.bots.metrics.services;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Короткое сообщение об ошибке для админов вместо полного стектрейса:
 * бот / часть приложения, откуда пришёл вызов (шедулер, HTTP, Telegram), исключение и его корневая причина,
 * цепочка вызовов только по классам приложения (без Spring / JDK) и ссылка на строку в GitHub.
 * Полный стектрейс остаётся в логах сервера.
 */
@Component
public class ErrorReportFormatter {

    static final String APP_PACKAGE = "com.kuklin.manageapp.";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(ZONE);
    private static final int MAX_TEXT = 300;
    private static final int MAX_CHAIN = 10;

    // Пакет бота → BotIdentifier; остальные части приложения — своими названиями
    private static final Map<String, String> BOT_PACKAGES = Map.ofEntries(
            Map.entry("caloriebot", "CALORIE_BOT"),
            Map.entry("hhparserbot", "HH_BOT"),
            Map.entry("bookingbot", "BOOKING_BOT"),
            Map.entry("aiassistantcalendar", "ASSISTANT_BOT"),
            Map.entry("pomidorotimer", "POMIDORO_BOT"),
            Map.entry("deparrbot", "AVIA_BOT"),
            Map.entry("kworkparser", "KWORK"),
            Map.entry("metrics", "METRICS"),
            Map.entry("channelposter", "CHANNEL_POSTER"),
            Map.entry("nicotinebot", "NICOTINE_BOT"),
            Map.entry("payment", "PAYMENT")
    );
    private static final Map<String, String> APP_PARTS = Map.of(
            "payment", "Оплата",
            "aiconversation", "ИИ (общий)",
            "common", "Общее"
    );

    private final String repoUrl;
    private final String commit;

    public ErrorReportFormatter(@Value("${metrics.errors.repo-url:}") String repoUrl,
                                @Value("${metrics.errors.commit:}") String commit) {
        this.repoUrl = repoUrl;
        this.commit = commit;
    }

    /** Сообщение в Telegram (HTML). */
    public String format(ILoggingEvent event) {
        IThrowableProxy throwable = event.getThrowableProxy();
        List<StackTraceElement> chain = appChain(event);
        StackTraceElement failedAt = chain.isEmpty() ? null : chain.get(chain.size() - 1);

        StringBuilder sb = new StringBuilder();
        sb.append("🔴 <b>").append(escape(part(event, chain))).append("</b> · ")
                .append(escape(context(event.getThreadName()))).append("\n");

        String message = event.getFormattedMessage();
        if (message != null && !message.isBlank()) {
            sb.append(escape(cut(message))).append("\n");
        }

        if (throwable != null) {
            sb.append("\n<b>").append(escape(simpleName(throwable.getClassName()))).append("</b>: ")
                    .append(escape(cut(throwable.getMessage()))).append("\n");
            IThrowableProxy root = rootCause(throwable);
            if (root != throwable) {
                sb.append("↳ корень: <b>").append(escape(simpleName(root.getClassName()))).append("</b>: ")
                        .append(escape(cut(root.getMessage()))).append("\n");
            }
        }

        if (!chain.isEmpty()) {
            sb.append("\n<b>Цепочка:</b>\n");
            appendChain(sb, chain);
            String link = sourceLink(failedAt);
            if (link != null) {
                sb.append("🔗 <a href=\"").append(escape(link)).append("\">")
                        .append(escape(failedAt.getFileName() + ":" + failedAt.getLineNumber())).append("</a>\n");
            }
        }

        sb.append("\n<i>").append(escape(event.getThreadName())).append(" · ")
                .append(TIME.format(Instant.ofEpochMilli(event.getTimeStamp()))).append("</i>");
        return sb.toString();
    }

    /**
     * Ключ «та же ошибка» для защиты от шквала: исключение + место в коде приложения;
     * без исключения — логгер + шаблон сообщения (без подставленных значений).
     */
    public String signature(ILoggingEvent event) {
        List<StackTraceElement> chain = appChain(event);
        String place = chain.isEmpty() ? event.getLoggerName() : frame(chain.get(chain.size() - 1));
        IThrowableProxy throwable = event.getThrowableProxy();
        return throwable != null
                ? throwable.getClassName() + " @ " + place
                : place + " : " + event.getMessage();
    }

    /** Одна строка для сводки «повторилась N раз». */
    public String title(ILoggingEvent event) {
        List<StackTraceElement> chain = appChain(event);
        IThrowableProxy throwable = event.getThrowableProxy();
        String what = throwable != null ? simpleName(throwable.getClassName()) : cut(event.getFormattedMessage());
        String where = chain.isEmpty() ? simpleName(event.getLoggerName()) : frame(chain.get(chain.size() - 1));
        return part(event, chain) + " · " + what + " @ " + where;
    }

    /**
     * Вызовы в коде приложения — от точки входа (шедулер, контроллер, хендлер) к месту ошибки.
     * Берётся из стектрейса исключения (если в нём нет кадров приложения — из причин),
     * без исключения — место вызова log.error.
     */
    List<StackTraceElement> appChain(ILoggingEvent event) {
        for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
            List<StackTraceElement> chain = new ArrayList<>();
            for (StackTraceElementProxy proxy : t.getStackTraceElementProxyArray()) {
                StackTraceElement element = proxy.getStackTraceElement();
                if (isAppFrame(element)) chain.add(0, element);
            }
            if (!chain.isEmpty()) return chain;
        }
        StackTraceElement[] callerData = event.getCallerData();
        if (callerData != null && callerData.length > 0 && isAppFrame(callerData[0])) {
            return List.of(callerData[0]);
        }
        return List.of();
    }

    String sourceLink(StackTraceElement element) {
        if (element == null || repoUrl.isBlank() || commit.isBlank()
                || element.getFileName() == null || element.getLineNumber() <= 0) {
            return null;
        }
        String className = element.getClassName();
        String packagePath = className.substring(0, className.lastIndexOf('.')).replace('.', '/');
        return repoUrl + "/blob/" + commit + "/src/main/java/" + packagePath + "/"
                + element.getFileName() + "#L" + element.getLineNumber();
    }

    private static boolean isAppFrame(StackTraceElement element) {
        String className = element.getClassName();
        // $$SpringCGLIB$$ — прокси Spring, строки <generated> в нём ничего не говорят
        return className.startsWith(APP_PACKAGE) && !className.contains("$$");
    }

    private static void appendChain(StringBuilder sb, List<StackTraceElement> chain) {
        List<String> lines = new ArrayList<>();
        for (StackTraceElement element : chain) lines.add(escape(frame(element)));
        if (lines.size() > MAX_CHAIN) {
            int skipped = lines.size() - MAX_CHAIN + 1;
            List<String> shortened = new ArrayList<>(lines.subList(0, 3));
            shortened.add("… ещё " + skipped);
            shortened.addAll(lines.subList(lines.size() - (MAX_CHAIN - 4), lines.size()));
            lines = shortened;
        }
        int last = lines.size() - 1;
        lines.set(last, "<b>" + lines.get(last) + "</b>  ← здесь");
        sb.append(lines.get(0)).append("\n");
        for (int i = 1; i < lines.size(); i++) sb.append("→ ").append(lines.get(i)).append("\n");
    }

    // BookingService.getAllBookingsByStatus:193
    private static String frame(StackTraceElement element) {
        return simpleName(element.getClassName()) + "." + element.getMethodName() + ":" + element.getLineNumber();
    }

    /** Бот или часть приложения — по точке входа в цепочке, иначе по логгеру. */
    private static String part(ILoggingEvent event, List<StackTraceElement> chain) {
        String className = chain.isEmpty() ? event.getLoggerName() : chain.get(0).getClassName();
        if (className == null || !className.startsWith(APP_PACKAGE)) return "Библиотека";
        String[] path = className.substring(APP_PACKAGE.length()).split("\\.");
        if (path.length > 1 && path[0].equals("bots")) {
            return BOT_PACKAGES.getOrDefault(path[1], path[1]);
        }
        return APP_PARTS.getOrDefault(path[0], path[0]);
    }

    /** Откуда пришёл вызов — по имени потока. */
    static String context(String threadName) {
        if (threadName == null) return "?";
        if (threadName.startsWith("scheduling-")) return "шедулер";
        if (threadName.startsWith("http-nio-")) return "HTTP-запрос";
        if (threadName.startsWith("task-")) return "Telegram-апдейт";
        if (threadName.contains("gram Connection") || threadName.contains("Telegram")) return "Telegram-сессия";
        if (threadName.equals("main")) return "запуск приложения";
        return threadName;
    }

    private static IThrowableProxy rootCause(IThrowableProxy throwable) {
        IThrowableProxy root = throwable;
        while (root.getCause() != null) root = root.getCause();
        return root;
    }

    private static String simpleName(String className) {
        if (className == null) return "?";
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static String cut(String text) {
        if (text == null) return "";
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT) + "…";
    }

    static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
