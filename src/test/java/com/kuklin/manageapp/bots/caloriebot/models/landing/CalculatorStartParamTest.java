package com.kuklin.manageapp.bots.caloriebot.models.landing;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile.ActivityLevel;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile.Goal;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile.Sex;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CalculatorStartParamTest {

    private static final CalculatorStartParam.Calculator CALC = new CalculatorStartParam.Calculator(
            Sex.FEMALE, 28, 168, 65, ActivityLevel.MEDIUM, Goal.LOSE_WEIGHT);

    @Test
    void parsesCalculatorWithoutUtm() {
        var param = CalculatorStartParam.parse("p_F_28_168_65_M_L");

        assertThat(param.calculator()).isEqualTo(CALC);
        assertThat(param.utmCode()).isNull();
    }

    @Test
    void utmTailMayContainUnderscores() {
        var param = CalculatorStartParam.parse("p_M_40_180_90_H_G_partner_ab12cd34");

        assertThat(param.calculator()).isEqualTo(new CalculatorStartParam.Calculator(
                Sex.MALE, 40, 180, 90, ActivityLevel.HIGH, Goal.GAIN_WEIGHT));
        assertThat(param.utmCode()).isEqualTo("partner_ab12cd34");
    }

    @Test
    void plainUtmCodeStaysUtm() {
        var param = CalculatorStartParam.parse("partner_ab12cd34");

        assertThat(param.calculator()).isNull();
        assertThat(param.utmCode()).isEqualTo("partner_ab12cd34");
    }

    @Test
    void brokenCalculatorIsDroppedButUtmCounts() {
        assertThat(CalculatorStartParam.parse("p_F_28_168_999_M_L_ref42").calculator()).isNull();
        assertThat(CalculatorStartParam.parse("p_F_28_168_999_M_L_ref42").utmCode()).isEqualTo("ref42");
        assertThat(CalculatorStartParam.parse("p_X_28_168_65_M_L").calculator()).isNull();
        assertThat(CalculatorStartParam.parse("p_F_-1_168_65_M_L").calculator()).isNull();
        assertThat(CalculatorStartParam.parse("p_F_28_168").calculator()).isNull();
        assertThat(CalculatorStartParam.parse(null).calculator()).isNull();
    }

    @Test
    void encodeRoundTripsAndFitsCallbackData() {
        String encoded = CALC.encode();

        assertThat(encoded).isEqualTo("p_F_28_168_65_M_L");
        assertThat(CalculatorStartParam.parse(encoded).calculator()).isEqualTo(CALC);
        // callback_data — до 64 байт вместе с командой
        assertThat(("/calcapply " + new CalculatorStartParam.Calculator(
                Sex.FEMALE, 140, 280, 500, ActivityLevel.MEDIUM, Goal.MAINTAIN).encode()).length()).isLessThanOrEqualTo(64);
    }

    @Test
    void sameAsComparesProfileParams() {
        UserNutritionProfile profile = new UserNutritionProfile()
                .setSex(Sex.FEMALE).setAgeYears(28).setHeightCm(168)
                .setCurrentWeightKg(new BigDecimal("65.00"))
                .setActivityLevel(ActivityLevel.MEDIUM).setGoal(Goal.LOSE_WEIGHT);

        assertThat(CALC.sameAs(profile)).isTrue();
        assertThat(CALC.sameAs(profile.setCurrentWeightKg(new BigDecimal("65.5")))).isFalse();
    }
}
