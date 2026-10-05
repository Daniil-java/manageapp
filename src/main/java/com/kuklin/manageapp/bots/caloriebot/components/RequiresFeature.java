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
 * <li><b>До вызова:</b> Проверяет тариф и сразу списывает попытку для указанной {@link BotFeature}
 * (атомарно — параллельные запросы не проходят сверх лимита). Если лимит исчерпан, метод не вызывается,
 * возвращается {@link AccessResult#denied(BotFeature)}.</li>
 * <li><b>После вызова:</b> Попытка возвращается, если метод бросил исключение или вернул пустой результат
 * (data == null) — услуга не оказана. Исключение — {@link #forgiveEmptyOncePerDay()}.</li>
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

    /**
     * Пустой результат значит «ИИ ответил, но ничего не нашёл» (например, на фото нет еды), а не техническую ошибку.
     * Тогда первый такой раз за день попытку возвращаем, а следующие — списываем: ИИ всё равно вызывался.
     * Что произошло, аспект пишет в {@link AccessResult#emptyResultCharge()} — вызывающий код предупреждает пользователя.
     * Технические ошибки должны вылетать исключением — тогда попытка возвращается всегда.
     */
    boolean forgiveEmptyOncePerDay() default false;
}