package com.kuklin.manageapp.bots.caloriebot.support;

import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.methods.forum.CloseForumTopic;
import org.telegram.telegrambots.meta.api.methods.forum.CreateForumTopic;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageId;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.forum.ForumTopic;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportGroupServiceTest {
    private static final long GROUP = -1001234567890L;
    private static final long USER = 7L;
    private static final long USER_TG = 555L;
    private static final int TOPIC = 77;

    private final SupportTicketRepository ticketRepository = mock(SupportTicketRepository.class);
    private final SupportMessageRepository messageRepository = mock(SupportMessageRepository.class);
    private final SupportTicketCardBuilder cardBuilder = mock(SupportTicketCardBuilder.class);
    private final TelegramUserService telegramUserService = mock(TelegramUserService.class);
    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final TelegramUser telegramUser = new TelegramUser();
    private CalorieTexts texts;
    private SupportGroupService service;

    @BeforeEach
    void setUp() {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(USER)).thenReturn("ru");
        texts = new CalorieTexts(new I18nProperties(), settings);
        SupportProperties properties = new SupportProperties();
        properties.setChatId(GROUP);

        telegramUser.setAppUserId(USER);
        telegramUser.setTelegramId(USER_TG);
        when(telegramUserService.findByAppUserIdAndBotIdentifier(USER, BotIdentifier.CALORIE_BOT))
                .thenReturn(Optional.of(telegramUser));
        when(ticketRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cardBuilder.buildTopicName(any(), any())).thenReturn("#12 🐞 Имя");
        when(cardBuilder.buildCard(any(), any())).thenReturn("card");

        service = new SupportGroupService();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "ticketService",
                new SupportTicketService(ticketRepository, messageRepository, properties));
        ReflectionTestUtils.setField(service, "cardBuilder", cardBuilder);
        ReflectionTestUtils.setField(service, "telegramUserService", telegramUserService);
        ReflectionTestUtils.setField(service, "texts", texts);
        ReflectionTestUtils.setField(service, "bot", bot);
    }

    private static SupportTicket buildTicket(SupportTicket.Status status) {
        return new SupportTicket().setId(12L).setAppUserId(USER).setStatus(status)
                .setCategory(SupportTicket.Category.BUG).setTopicId(status == SupportTicket.Status.DRAFT ? null : TOPIC);
    }

    private static MessageId buildMessageId(long id) {
        MessageId messageId = new MessageId();
        messageId.setMessageId(id);
        return messageId;
    }

    private static Update buildGroupUpdate(String text) {
        Message message = new Message();
        message.setChat(new Chat(GROUP, "supergroup"));
        message.setMessageThreadId(TOPIC);
        message.setMessageId(300);
        User admin = new User();
        admin.setId(1L);
        admin.setIsBot(false);
        message.setFrom(admin);
        message.setText(text);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    @Test
    void firstUserMessageCreatesTopicWithCardAndCopy() throws Exception {
        SupportTicket draft = buildTicket(SupportTicket.Status.DRAFT);
        ForumTopic topic = new ForumTopic();
        topic.setMessageThreadId(TOPIC);
        when(bot.execute(any(CreateForumTopic.class))).thenReturn(topic);
        when(bot.execute(any(CopyMessage.class))).thenReturn(buildMessageId(900));
        Message message = new Message();
        message.setChat(new Chat(USER_TG, "private"));
        message.setMessageId(50);
        message.setText("не открывается отчёт");

        boolean created = service.submitUserMessage(draft, telegramUser, message);

        assertThat(created).isTrue();
        assertThat(draft.getStatus()).isEqualTo(SupportTicket.Status.OPEN);
        assertThat(draft.getTopicId()).isEqualTo(TOPIC);
        assertThat(draft.getAwaitingTextSince()).isNull();
        ArgumentCaptor<SendMessage> card = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).sendReturnedMessage(card.capture());
        assertThat(card.getValue().getMessageThreadId()).isEqualTo(TOPIC);
        assertThat(card.getValue().getText()).isEqualTo("card");
        ArgumentCaptor<SupportMessage> saved = ArgumentCaptor.forClass(SupportMessage.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getDirection()).isEqualTo(SupportMessage.Direction.USER);
        assertThat(saved.getValue().getText()).isEqualTo("не открывается отчёт");
        assertThat(saved.getValue().getUserMessageId()).isEqualTo(50);
        assertThat(saved.getValue().getGroupMessageId()).isEqualTo(900);
    }

    @Test
    void supportReplyGoesToUserWithHeader() {
        when(ticketRepository.findByTopicId(TOPIC)).thenReturn(Optional.of(buildTicket(SupportTicket.Status.OPEN)));
        Message sent = new Message();
        sent.setMessageId(60);
        when(bot.sendReturnedMessage(eq(USER_TG), anyString(), any(), isNull())).thenReturn(sent);

        service.handleGroupUpdate(buildGroupUpdate("Проверили — <всё> вернули"));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot).sendReturnedMessage(eq(USER_TG), text.capture(), any(), isNull());
        assertThat(text.getValue())
                .startsWith(texts.get(USER, BotTextKeys.SUPPORT_REPLY_HEADER, "12"))
                .endsWith("Проверили — &lt;всё&gt; вернули");
        ArgumentCaptor<SupportMessage> saved = ArgumentCaptor.forClass(SupportMessage.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getDirection()).isEqualTo(SupportMessage.Direction.SUPPORT);
        assertThat(saved.getValue().getUserMessageId()).isEqualTo(60);
    }

    @Test
    void noteIsNotSentToUser() {
        when(ticketRepository.findByTopicId(TOPIC)).thenReturn(Optional.of(buildTicket(SupportTicket.Status.OPEN)));

        service.handleGroupUpdate(buildGroupUpdate("!проверить платёж в ЮKassa"));

        verify(bot, never()).sendReturnedMessage(anyLong(), anyString(), any(), any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void closeCommandClosesTicketAndAsksForRating() throws Exception {
        SupportTicket ticket = buildTicket(SupportTicket.Status.OPEN);
        when(ticketRepository.findByTopicId(TOPIC)).thenReturn(Optional.of(ticket));

        service.handleGroupUpdate(buildGroupUpdate("/close"));

        assertThat(ticket.getStatus()).isEqualTo(SupportTicket.Status.CLOSED);
        assertThat(ticket.getClosedAt()).isNotNull();
        String closedNotice = texts.get(USER, BotTextKeys.SUPPORT_CLOSED_NOTICE, "12");
        verify(bot).sendReturnedMessage(eq(USER_TG), eq(closedNotice), any(), isNull());
        verify(bot).execute(any(CloseForumTopic.class));
    }

    @Test
    void reminderOnlyForUnansweredTickets() {
        SupportProperties properties = (SupportProperties) ReflectionTestUtils.getField(service, "properties");
        properties.setRemindAfter(java.time.Duration.ofHours(12));
        SupportTicket waiting = buildTicket(SupportTicket.Status.OPEN)
                .setLastUserMessageAt(java.time.Instant.now().minus(java.time.Duration.ofHours(13)));
        when(ticketRepository.findUnanswered(any())).thenReturn(List.of(waiting));

        service.remindUnanswered();

        ArgumentCaptor<SendMessage> reminder = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot, atLeastOnce()).sendReturnedMessage(reminder.capture());
        assertThat(reminder.getValue().getText()).contains("13 ч");
        assertThat(waiting.getRemindedAt()).isNotNull();
    }
}
