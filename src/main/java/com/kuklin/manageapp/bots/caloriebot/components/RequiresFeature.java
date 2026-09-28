package com.kuklin.manageapp.bots.caloriebot.components;

import com.kuklin.manageapp.bots.caloriebot.models.feature.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * Маркерная аннотация для декларативного контроля доступа к функциям бота.
 * Обрабатывается через {@link FeatureAccessAspect}.
 * * <p><b>Механика работы:</b></p>
 * <ul>
 * <li><b>До вызова:</b> Проверяет наличие активной подписки и остаток лимита для указанной {@link BotFeature}.</li>
 * <li><b>Выполнение:</b> Если лимит исчерпан, метод не вызывается, возвращается {@link AccessResult#denied(BotFeature)}.</li>
 * <li><b>После вызова:</b> Если метод вернул результат, содержащий данные (не null), счетчик использования
 * автоматически инкрементируется в БД.</li>
 * </ul>
 * * <p><b>Требования к сигнатуре метода:</b></p>
 * <ol>
 * <li>Метод обязан принимать аргумент типа {@code Long} (идентификатор пользователя в Telegram).</li>
 * <li>Метод обязан возвращать {@link AccessResult} (для корректного управления списанием лимитов).</li>
 * </ol>
 * * @see BotFeature
 * @see FeatureAccessAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {

    /**
     * Тип функциональности, доступ к которой нужно ограничить.
     */
    BotFeature value();

    /**
     * Контекст бота, в рамках которого проверяются лимиты (поддержка мультиботности).
     */
    BotIdentifier botIdentifier();
}