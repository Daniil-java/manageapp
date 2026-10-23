package com.kuklin.manageapp.bots.metrics.services;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import java.net.SocketException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorReportFormatterTest {

    private static final String COMMIT = "cdb0ec8";
    private final ErrorReportFormatter formatter =
            new ErrorReportFormatter("https://github.com/Daniil-java/manageapp", COMMIT);

    @Test
    void bookingDbErrorShowsBotChainRootCauseAndLink() {
        LoggingEvent event = event("scheduling-2", "Scheduled task failed", bookingDbError());

        String text = formatter.format(event);

        assertThat(text).startsWith("🔴 <b>BOOKING_BOT</b> · шедулер");
        assertThat(text).contains("<b>DataAccessResourceFailureException</b>: JDBC exception executing SQL");
        assertThat(text).contains("↳ корень: <b>SocketException</b>: Connection reset");
        assertThat(text).contains("""
                BookingSchedulerService.ordersCheckUpdateScheduleProcessor:18
                → BookingStatusScheduleProcessor.process:33
                → <b>BookingService.getAllBookingsByStatus:193</b>  ← здесь
                """);
        // Spring, JDK и прокси в цепочку не попадают
        assertThat(text).doesNotContain("HibernateJpaDialect", "Method.invoke", "SpringCGLIB", "<generated>");
        assertThat(text).contains("https://github.com/Daniil-java/manageapp/blob/cdb0ec8/src/main/java/"
                + "com/kuklin/manageapp/bots/bookingbot/services/BookingService.java#L193");
    }

    @Test
    void noLinkWithoutCommit() {
        ErrorReportFormatter local = new ErrorReportFormatter("https://github.com/Daniil-java/manageapp", "");

        assertThat(local.format(event("scheduling-1", "x", bookingDbError()))).doesNotContain("github.com");
    }

    @Test
    void htmlInMessagesIsEscaped() {
        String text = formatter.format(event("http-nio-8095-exec-3", "bad <tag> & co", null));

        assertThat(text).contains("bad &lt;tag&gt; &amp; co").contains("HTTP-запрос");
    }

    @Test
    void sameErrorFromSamePlaceHasSameSignature() {
        String first = formatter.signature(event("scheduling-1", "a", bookingDbError()));
        String second = formatter.signature(event("scheduling-3", "b", bookingDbError()));

        assertThat(first).isEqualTo(second)
                .isEqualTo("org.springframework.dao.DataAccessResourceFailureException @ BookingService.getAllBookingsByStatus:193");
    }

    @Test
    void threadNameToContext() {
        assertThat(ErrorReportFormatter.context("task-4")).isEqualTo("Telegram-апдейт");
        assertThat(ErrorReportFormatter.context("main")).isEqualTo("запуск приложения");
        assertThat(ErrorReportFormatter.context("custom-thread")).isEqualTo("custom-thread");
    }

    // Стектрейс как у ошибки 08.10: Spring Data → наш сервис → прокси → процессор → шедулер
    private static Exception bookingDbError() {
        Exception error = new org.springframework.dao.DataAccessResourceFailureException(
                "JDBC exception executing SQL [select … from bookings b1_0 where b1_0.status=?]",
                new SocketException("Connection reset"));
        error.setStackTrace(new StackTraceElement[]{
                frame("org.springframework.orm.jpa.vendor.HibernateJpaDialect", "convertHibernateAccessException", "HibernateJpaDialect.java", 278),
                frame("jdk.proxy2.$Proxy216", "findAllByStatus", null, -1),
                frame("com.kuklin.manageapp.bots.bookingbot.services.BookingService", "getAllBookingsByStatus", "BookingService.java", 193),
                frame("java.lang.reflect.Method", "invoke", "Method.java", 568),
                frame("com.kuklin.manageapp.bots.bookingbot.services.BookingService$$SpringCGLIB$$0", "getAllBookingsByStatus", "<generated>", -1),
                frame("com.kuklin.manageapp.bots.bookingbot.processors.BookingStatusScheduleProcessor", "process", "BookingStatusScheduleProcessor.java", 33),
                frame("org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor", "intercept", "CglibAopProxy.java", 728),
                frame("com.kuklin.manageapp.bots.bookingbot.services.BookingSchedulerService", "ordersCheckUpdateScheduleProcessor", "BookingSchedulerService.java", 18),
                frame("java.lang.Thread", "run", "Thread.java", 833),
        });
        return error;
    }

    private static StackTraceElement frame(String className, String method, String file, int line) {
        return new StackTraceElement(className, method, file, line);
    }

    private static LoggingEvent event(String thread, String message, Throwable throwable) {
        Logger logger = new LoggerContext().getLogger("com.kuklin.manageapp.bots.bookingbot.services.BookingSchedulerService");
        LoggingEvent event = new LoggingEvent(Logger.class.getName(), logger, Level.ERROR, message, throwable, null);
        event.setThreadName(thread);
        event.setCallerData(new StackTraceElement[0]);
        return event;
    }
}
