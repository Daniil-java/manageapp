package com.kuklin.manageapp.common.configurations;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.kuklin.manageapp.bots.metrics.telegram.handlers.MetricsErrorParseUpdateHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class LoggingConfig {
    private final MetricsErrorParseUpdateHandler metricsErrorParseUpdateHandler;

    public class ErrorLogAdminNotifierAppender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent eventObject) {
            metricsErrorParseUpdateHandler.sendErrorMessageToAdmin(eventObject);
        }
    }

    @Bean
    public ErrorLogAdminNotifierAppender customAppender() {
        ErrorLogAdminNotifierAppender appender = new ErrorLogAdminNotifierAppender();
        appender.setName("ErrorLogAdminNotifierAppender");
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());

        // Set filter for ERROR level only
        ThresholdFilter filter = new ThresholdFilter();
        filter.setLevel(Level.ERROR.toString());
        filter.start();
        appender.addFilter(filter);

        appender.start();
        return appender;
    }

    @Bean
    public LoggerContext loggerContext() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(customAppender());
        return context;
    }
}
