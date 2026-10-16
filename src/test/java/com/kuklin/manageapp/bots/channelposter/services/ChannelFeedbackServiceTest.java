package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.caloriebot.components.services.UtmService;
import com.kuklin.manageapp.bots.channelposter.entities.ChannelDailyStats;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.repositories.ChannelDailyStatsRepository;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.reactions.MessageReactionCountUpdated;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionCount;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChannelFeedbackServiceTest {

    private static final long CHANNEL = -100L;

    private final PostQueueService postQueueService = mock(PostQueueService.class);
    private final ChannelDailyStatsRepository statsRepository = mock(ChannelDailyStatsRepository.class);
    private final UtmService utmService = mock(UtmService.class);
    private final ChannelPosterBotKeyComponent keys = mock(ChannelPosterBotKeyComponent.class);
    private final ChannelFeedbackService service = new ChannelFeedbackService(postQueueService, statsRepository, utmService, keys);

    @BeforeEach
    void setUp() {
        when(keys.getChannelId()).thenReturn(CHANNEL);
        when(utmService.countChannelClicksSince(any())).thenReturn(new UtmService.ChannelClicks(4, 3));
    }

    @Test
    void sumsReactionsOfChannelPost() {
        PostQueue post = new PostQueue().setId(1L);
        when(postQueueService.findSentByTgMessageId(77)).thenReturn(Optional.of(post));

        service.updateReactions(reactions(CHANNEL, 77, 5, 3));

        assertThat(post.getReactions()).isEqualTo(8);
        assertThat(post.getReactionsUpdatedAt()).isNotNull();
        verify(postQueueService).save(post);
    }

    @Test
    void ignoresOtherChats() {
        service.updateReactions(reactions(42L, 77, 5));

        verifyNoInteractions(postQueueService);
    }

    @Test
    void botLinkCarriesPostCode() {
        when(utmService.getOrCreateChannelPostCode(12L)).thenReturn("zch12");

        assertThat(service.getBotLink(12L)).isEqualTo("https://t.me/calorydairy_bot?start=zch12");
        assertThat(ChannelFeedbackService.buildBotLinkLine("https://t.me/b?start=x&y"))
                .isEqualTo("📸 Калории своей тарелки по фото — <a href=\"https://t.me/b?start=x&amp;y\">@calorydairy_bot</a>");
    }

    @Test
    void reportShowsGrowthClicksAndTopPosts() {
        when(statsRepository.findById(any())).thenReturn(Optional.empty());
        when(statsRepository.findById(LocalDate.now(ZoneId.of("Europe/Moscow")).minusDays(1)))
                .thenReturn(Optional.of(new ChannelDailyStats().setSubscribers(115)));
        when(postQueueService.getTopReacted(any(), anyInt()))
                .thenReturn(List.of(new PostQueue().setId(3L).setTitle("Белок <утром>").setReactions(12)));

        String section = service.buildReportSection(120, true);

        assertThat(section)
                .contains("👥 Подписчики: 120 (+5 за сутки)")
                .contains("🔗 Из постов в бота: за сутки 4 (впервые 3), за неделю 4")
                .contains("• #3 Белок &lt;утром&gt; — 12");
        verify(statsRepository).save(any(ChannelDailyStats.class));
    }

    @Test
    void reportWithoutTelegramAnswerSavesNothing() {
        when(postQueueService.getTopReacted(any(), anyInt())).thenReturn(List.of());

        assertThat(service.buildReportSection(null, true)).contains("Подписчики: Telegram не ответил");
        verify(statsRepository, never()).save(any());
    }

    @Test
    void growthFormatting() {
        ChannelDailyStats before = new ChannelDailyStats().setSubscribers(100);
        assertThat(ChannelFeedbackService.formatGrowth(90, Optional.of(before), "за неделю")).isEqualTo("-10 за неделю");
        assertThat(ChannelFeedbackService.formatGrowth(100, Optional.of(before), "за сутки")).isEqualTo("0 за сутки");
        assertThat(ChannelFeedbackService.formatGrowth(100, Optional.empty(), "за сутки")).isEmpty();
    }

    private static MessageReactionCountUpdated reactions(long chatId, int messageId, int... counts) {
        Chat chat = new Chat();
        chat.setId(chatId);
        MessageReactionCountUpdated update = new MessageReactionCountUpdated();
        update.setChat(chat);
        update.setMessageId(messageId);
        update.setReactions(Arrays.stream(counts).mapToObj(c -> {
            ReactionCount count = new ReactionCount();
            count.setTotalCount(c);
            return count;
        }).toList());
        return update;
    }
}
