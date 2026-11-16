package com.kuklin.manageapp.bots.caloriebot.referral;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.referral.telegram.InviteLinks;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Мини-апп получает ту же реферальную ссылку, что кнопка «Переслать другу» в боте. */
class ReferralControllerTest {

    private static final long USER = 7L;

    @Test
    void returnsBotReferralLink() {
        CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
        UtmService utmService = mock(UtmService.class);
        when(bot.getBotUsername()).thenReturn("@calorydairy_bot");
        when(utmService.getOrCreateReferralLink(USER)).thenReturn("ref7");
        InviteLinks links = new InviteLinks(bot, utmService, mock(ReferralRewardService.class), mock(CalorieTexts.class));

        ReferralController.ReferralLinkDto link = new ReferralController(links).getReferralLink(USER);

        assertThat(link.url()).isEqualTo("https://t.me/calorydairy_bot?start=ref7");
    }
}
