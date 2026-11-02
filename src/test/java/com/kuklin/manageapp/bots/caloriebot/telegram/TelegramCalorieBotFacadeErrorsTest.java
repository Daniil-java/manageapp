package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.aiconversation.models.AiUnavailableException;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.SupportInputUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.services.TelegramUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramCalorieBotFacadeErrorsTest {

    private static final long USER = 7L;
    private static final long CHAT = 1007L;

    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final UpdateHandler handler = mock(UpdateHandler.class);
    private CalorieTexts texts;
    private TelegramCalorieBotFacade facade;

    @BeforeEach
    void setUp() {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(USER)).thenReturn("ru");
        texts = new CalorieTexts(new I18nProperties(), settings);

        TelegramUser telegramUser = new TelegramUser();
        telegramUser.setAppUserId(USER);
        TelegramUserService userService = mock(TelegramUserService.class);
        when(userService.createOrGetUserByTelegram(eq(BotIdentifier.CALORIE_BOT), any())).thenReturn(telegramUser);

        facade = new TelegramCalorieBotFacade();
        ReflectionTestUtils.setField(facade, "telegramUserService", userService);
        ReflectionTestUtils.setField(facade, "menuButtons", mock(CalorieMenuButtons.class));
        ReflectionTestUtils.setField(facade, "userSettingsService", settings);
        ReflectionTestUtils.setField(facade, "texts", texts);
        ReflectionTestUtils.setField(facade, "calorieTelegramBot", bot);
        ReflectionTestUtils.setField(facade, "supportInputUpdateHandler", mock(SupportInputUpdateHandler.class));
        facade.register(Command.CALORIE_GENERAL.getCommandText(), handler);
        facade.register(Command.PAYMENT_PRE_CHECK_QUERY.getCommandText(), handler);
        facade.register(Command.PAYMENT_SUCCESS.getCommandText(), handler);
    }

    private static User buildUser() {
        User user = new User();
        user.setId(CHAT);
        return user;
    }

    private static Update buildTextUpdate() {
        Message message = new Message();
        message.setFrom(buildUser());
        message.setChat(new Chat(CHAT, "private"));
        message.setText("гречка 200 г");
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private String getSentText() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot).sendReturnedMessage(eq(CHAT), text.capture(), any(), any());
        return text.getValue();
    }

    @Test
    void unexpectedExceptionGetsGenericReply() {
        Update update = buildTextUpdate();
        doThrow(new IllegalStateException("db down")).when(handler).handle(eq(update), any());

        facade.handleUpdate(update);

        assertThat(getSentText()).isEqualTo(texts.error(USER, ErrorStatus.UNEXPECTED_ERROR)).doesNotContain("/support");
    }

    @Test
    void errorResponseExceptionGetsItsOwnText() {
        Update update = buildTextUpdate();
        doThrow(new ErrorResponseException(ErrorStatus.DISH_SAVE_FAILED)).when(handler).handle(eq(update), any());

        facade.handleUpdate(update);

        assertThat(getSentText()).isEqualTo(texts.error(USER, ErrorStatus.DISH_SAVE_FAILED));
    }

    @Test
    void aiUnavailableGetsAiText() {
        Update update = buildTextUpdate();
        doThrow(new AiUnavailableException("OpenAI chat failed: HTTP 500", 500, null)).when(handler).handle(eq(update), any());

        facade.handleUpdate(update);

        assertThat(getSentText()).isEqualTo(texts.error(USER, ErrorStatus.AI_UNAVAILABLE));
    }

    @Test
    void failedSuccessfulPaymentSaysPaymentReceived() {
        Update update = buildTextUpdate();
        update.getMessage().setText(null);
        update.getMessage().setSuccessfulPayment(new SuccessfulPayment());
        doThrow(new IllegalStateException("db down")).when(handler).handle(eq(update), any());

        facade.handleUpdate(update);

        verify(bot).sendReturnedMessage(CHAT, texts.error(USER, ErrorStatus.PAYMENT_NOT_ACTIVATED));
    }

    @Test
    void failedPreCheckoutIsDeclined() throws Exception {
        PreCheckoutQuery query = new PreCheckoutQuery();
        query.setId("q1");
        query.setFrom(buildUser());
        Update update = new Update();
        update.setPreCheckoutQuery(query);
        doThrow(new IllegalStateException("db down")).when(handler).handle(eq(update), any());

        facade.handleUpdate(update);

        ArgumentCaptor<AnswerPreCheckoutQuery> answer = ArgumentCaptor.forClass(AnswerPreCheckoutQuery.class);
        verify(bot).execute(answer.capture());
        assertThat(answer.getValue().getOk()).isFalse();
        assertThat(answer.getValue().getPreCheckoutQueryId()).isEqualTo("q1");
        verify(bot, never()).sendReturnedMessage(anyLong(), any(), any(), any());
    }
}
