package com.kuklin.manageapp.bots.caloriebot.subscription.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.handlers.PaymentProviderChoiceUpdateHandler;
import com.kuklin.manageapp.payment.models.common.Currency;
import com.kuklin.manageapp.payment.services.PricingPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PremiumOfferButtonsTest {

    private final PricingPlanService pricingPlanService = mock(PricingPlanService.class);
    private final PaymentProviderChoiceUpdateHandler providerChoiceHandler = mock(PaymentProviderChoiceUpdateHandler.class);
    private PremiumOfferButtons buttons;

    @BeforeEach
    void setUp() {
        CalorieTexts texts = new CalorieTexts(new I18nProperties(), mock(UserSettingsService.class));
        buttons = new PremiumOfferButtons(pricingPlanService, providerChoiceHandler, texts);
        when(providerChoiceHandler.getHandlerListName()).thenReturn("PROVIDER");
    }

    @Test
    void planButtonsSortedByDurationWithPriceAndSaving() {
        when(pricingPlanService.getAllPlansByBotIdentifierAndPlanStatusAvailable(any())).thenReturn(List.of(
                buildPlan(3L, 365, 1990), buildPlan(1L, 30, 299), buildPlan(2L, 90, 699)));

        List<InlineKeyboardButton> planButtons = buttons.buildPlanButtons(AppLanguage.RU);

        assertThat(planButtons).extracting(InlineKeyboardButton::getText).containsExactly(
                "1 месяц — 299 ⭐", "3 месяца — 699 ⭐ · выгода 22%", "12 месяцев — 1990 ⭐ · выгода 45%");
        assertThat(planButtons.get(0).getCallbackData()).isEqualTo("PROVIDER 1");
    }

    @Test
    void premiumButtonShowsCheapestPriceAndOpensPlans() {
        when(pricingPlanService.getAllPlansByBotIdentifierAndPlanStatusAvailable(any())).thenReturn(List.of(
                buildPlan(2L, 90, 699), buildPlan(1L, 30, 299)));

        InlineKeyboardButton button = buttons.buildPremiumButton(AppLanguage.RU);

        assertThat(button.getText()).isEqualTo("💎 Premium — от 299 ⭐");
        assertThat(button.getCallbackData()).isEqualTo(Command.CALORIE_PAYMENT_PAYLOAD_PLAN.getCommandText());
        assertThat(buttons.buildExtendButton(AppLanguage.RU).getText()).isEqualTo("💎 Продлить — от 299 ⭐");
    }

    @Test
    void noPlansForSaleJustPlansButton() {
        when(pricingPlanService.getAllPlansByBotIdentifierAndPlanStatusAvailable(any())).thenReturn(List.of());

        assertThat(buttons.buildPremiumButton(AppLanguage.RU).getText()).isEqualTo("💎 Тарифы");
    }

    private static PricingPlan buildPlan(Long id, int days, int stars) {
        return new PricingPlan().setId(id).setDurationDays(days).setPriceMinor(stars).setCurrency(Currency.XTR);
    }
}
