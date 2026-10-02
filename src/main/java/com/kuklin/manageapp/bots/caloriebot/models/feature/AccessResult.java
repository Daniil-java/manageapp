package com.kuklin.manageapp.bots.caloriebot.models.feature;

import com.kuklin.manageapp.bots.caloriebot.models.exceptions.MissingFeatureException;

public record AccessResult<T>(
        T data,
        BotFeature deniedFeature,
        boolean isAllowed
) {
    public static <T> AccessResult<T> success(T data) {
        return new AccessResult<>(data, null, true);
    }

    public static <T> AccessResult<T> denied(BotFeature feature) {
        return new AccessResult<>(null, feature, false);
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
