package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.configurations.SupportProperties;
import com.kuklin.manageapp.bots.caloriebot.entities.SupportTicket;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.support.SupportGroupService;
import com.kuklin.manageapp.bots.caloriebot.support.SupportTicketService;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.Voice;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportInputUpdateHandlerTest {
    private static final long USER = 7L;
    private static final long CHAT = 1007L;

    private final SupportTicketService ticketService = mock(SupportTicketService.class);
    private final SupportGroupService groupService = mock(SupportGroupService.class);
    private final CalorieMenuButtons menuButtons = mock(CalorieMenuButtons.class);
    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final TelegramUser telegramUser = new TelegramUser();
    private CalorieTexts texts;
    private SupportInputUpdateHandler handler;

    @BeforeEach
    void setUp() {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(USER)).thenReturn("ru");
        texts = new CalorieTexts(new I18nProperties(), settings);
        SupportProperties properties = new SupportProperties();
        properties.setChatId(-1001L);
        telegramUser.setAppUserId(USER);
        when(menuButtons.findCommand(anyString())).thenReturn(Optional.empty());
        handler = new SupportInputUpdateHandler(properties, ticketService, groupService, menuButtons, bot, texts);
    }

    private static Update buildUpdate(Message message) {
        message.setChat(new Chat(CHAT, "private"));
        message.setMessageId(50);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private static Update buildTextUpdate(String text) {
        Message message = new Message();
        message.setText(text);
        return buildUpdate(message);
    }

    private static SupportTicket buildTicket(SupportTicket.Status status) {
        return new SupportTicket().setId(12L).setAppUserId(USER).setStatus(status)
                .setCategory(SupportTicket.Category.BUG);
    }

    @Test
    void plainMessageWithoutTicketIsFood() {
        assertThat(handler.isSupportInput(buildTextUpdate("гречка 200 г"), telegramUser)).isFalse();
    }

    @Test
    void messageAfterCategoryGoesToTicket() {
        when(ticketService.findAwaitingOrNull(USER)).thenReturn(buildTicket(SupportTicket.Status.DRAFT));

        assertThat(handler.isSupportInput(buildTextUpdate("не открывается отчёт"), telegramUser)).isTrue();
    }

    @Test
    void commandCancelsAwaitingAndIsNotTicket() {
        when(ticketService.findAwaitingOrNull(USER)).thenReturn(buildTicket(SupportTicket.Status.DRAFT));

        assertThat(handler.isSupportInput(buildTextUpdate(Command.CALORIE_MENU.getCommandText()), telegramUser)).isFalse();
        verify(ticketService).clearAwaiting(USER);
    }

    @Test
    void menuButtonCancelsAwaitingAndIsNotTicket() {
        when(ticketService.findAwaitingOrNull(USER)).thenReturn(buildTicket(SupportTicket.Status.OPEN));
        when(menuButtons.findCommand("📊СЕГОДНЯ")).thenReturn(Optional.of(Command.CALORIE_TODAY_LIST));

        assertThat(handler.isSupportInput(buildTextUpdate("📊СЕГОДНЯ"), telegramUser)).isFalse();
        verify(ticketService).clearAwaiting(USER);
    }

    @Test
    void replyToSupportMessageGoesToTicket() {
        Message supportReply = new Message();
        supportReply.setMessageId(40);
        User botUser = new User();
        botUser.setIsBot(true);
        supportReply.setFrom(botUser);
        Update update = buildTextUpdate("спасибо, а ещё вопрос");
        update.getMessage().setReplyToMessage(supportReply);
        when(ticketService.findByReplyOrNull(USER, 40)).thenReturn(buildTicket(SupportTicket.Status.OPEN));

        assertThat(handler.isSupportInput(update, telegramUser)).isTrue();
    }

    @Test
    void firstMessageOpensTicketWithoutReplyTimePromise() {
        SupportTicket draft = buildTicket(SupportTicket.Status.DRAFT);
        when(ticketService.findAwaitingOrNull(USER)).thenReturn(draft);
        Update update = buildTextUpdate("не открывается отчёт");
        when(groupService.submitUserMessage(draft, telegramUser, update.getMessage())).thenReturn(true);

        handler.handle(update, telegramUser);

        String created = texts.get(USER, BotTextKeys.SUPPORT_TICKET_CREATED, "12");
        assertThat(created).contains("№12").doesNotContain("час");
        verify(bot).sendReturnedMessage(eq(CHAT), eq(created), any(), any());
    }

    @Test
    void voiceIsNotAcceptedAndAwaitingStays() {
        when(ticketService.findAwaitingOrNull(USER)).thenReturn(buildTicket(SupportTicket.Status.DRAFT));
        Message message = new Message();
        message.setVoice(new Voice());

        handler.handle(buildUpdate(message), telegramUser);

        verify(bot).sendReturnedMessage(CHAT, texts.get(USER, BotTextKeys.SUPPORT_UNSUPPORTED_CONTENT));
        verify(groupService, never()).submitUserMessage(any(), any(), any());
        verify(ticketService, never()).clearAwaiting(USER);
    }

    @Test
    void replyToClosedTicketSuggestsNewOne() {
        Message supportReply = new Message();
        supportReply.setMessageId(40);
        User botUser = new User();
        botUser.setIsBot(true);
        supportReply.setFrom(botUser);
        Update update = buildTextUpdate("ещё вопрос");
        update.getMessage().setReplyToMessage(supportReply);
        when(ticketService.findByReplyOrNull(USER, 40)).thenReturn(buildTicket(SupportTicket.Status.CLOSED));

        handler.handle(update, telegramUser);

        verify(bot).sendReturnedMessage(CHAT, texts.get(USER, BotTextKeys.SUPPORT_TICKET_CLOSED, "12"));
        verify(groupService, never()).submitUserMessage(any(), any(), any());
    }
}
