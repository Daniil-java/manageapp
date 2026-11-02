package com.kuklin.manageapp.payment.components.texts;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentTextResolverTest {
    private static final long USER_ID = 7L;

    private final PaymentBotTexts calorieTexts = mock(PaymentBotTexts.class);
    private final PaymentTextResolver resolver = new PaymentTextResolver(List.of(calorieTexts));

    {
        when(calorieTexts.getBotIdentifier()).thenReturn(BotIdentifier.CALORIE_BOT);
    }

    private static PricingPlan plan(BotIdentifier bot) {
        return new PricingPlan().setBotIdentifier(bot).setTitle("Title from DB").setDescription("Description from DB");
    }

    @Test
    void botWithTextsGetsItsTranslations() {
        PricingPlan plan = plan(BotIdentifier.CALORIE_BOT);
        when(calorieTexts.getText(PaymentText.PRE_CHECKOUT_ERROR, USER_ID)).thenReturn("Payment data error");
        when(calorieTexts.getPlanTitle(plan, USER_ID)).thenReturn("Premium 1 Month");
        when(calorieTexts.getProviderTitle(Payment.Provider.STARS, USER_ID)).thenReturn("Stars");

        assertThat(resolver.getText(BotIdentifier.CALORIE_BOT, PaymentText.PRE_CHECKOUT_ERROR, USER_ID))
                .isEqualTo("Payment data error");
        assertThat(resolver.getPlanTitle(plan, USER_ID)).isEqualTo("Premium 1 Month");
        assertThat(resolver.getProviderTitle(BotIdentifier.CALORIE_BOT, Payment.Provider.STARS, USER_ID))
                .isEqualTo("Stars");
    }

    @Test
    void botWithoutTextsGetsDefaults() {
        BotIdentifier other = BotIdentifier.PAYMENT;
        PricingPlan plan = plan(other);

        assertThat(resolver.getText(other, PaymentText.PRE_CHECKOUT_ERROR, USER_ID)).isEqualTo("Ошибка данных платежа");
        assertThat(resolver.getPlanTitle(plan, USER_ID)).isEqualTo("Title from DB");
        assertThat(resolver.getPlanInvoiceDescription(plan, USER_ID)).isEqualTo("Description from DB");
        assertThat(resolver.getProviderTitle(other, Payment.Provider.STARS, USER_ID)).isEqualTo("Звезды");
    }
}
