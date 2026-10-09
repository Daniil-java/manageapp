package com.kuklin.manageapp.bots.caloriebot.models.feature;

import com.kuklin.manageapp.bots.caloriebot.models.exceptions.MissingFeatureException;

/**
 * @param emptyResultCharge что стало с попыткой при пустом результате — ставит
 *                          {@link com.kuklin.manageapp.bots.caloriebot.components.FeatureAccessAspect}
 */
public record AccessResult<T>(
        T data,
        BotFeature deniedFeature,
        boolean isAllowed,
        EmptyResultCharge emptyResultCharge
) {
    public static <T> AccessResult<T> success(T data) {
        return new AccessResult<>(data, null, true, EmptyResultCharge.NONE);
    }

    public static <T> AccessResult<T> denied(BotFeature feature) {
        return new AccessResult<>(null, feature, false, EmptyResultCharge.NONE);
    }

    public AccessResult<T> withEmptyResultCharge(EmptyResultCharge charge) {
        return new AccessResult<>(data, deniedFeature, isAllowed, charge);
    }

    // Метод, который заставит обработать результат
    public T getOrThrow() throws MissingFeatureException {
        if (!isAllowed) throw new MissingFeatureException(deniedFeature);
        return data;
    }

    public boolean dataIsNull() {
        return data == null;
    }
}
