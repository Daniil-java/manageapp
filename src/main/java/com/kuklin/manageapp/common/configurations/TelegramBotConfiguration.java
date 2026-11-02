package com.kuklin.manageapp.common.configurations;

import com.kuklin.manageapp.common.library.tgmodels.RefundedPaymentCapture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Getter
@Configuration
public class TelegramBotConfiguration {
    @Bean
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        // До регистрации ботов: первый getUpdates закеширует десериализатор Update
        RefundedPaymentCapture.install();
        return new TelegramBotsApi(DefaultBotSession.class);
    }

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource
                = new ReloadableResourceBundleMessageSource();

        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }
}
