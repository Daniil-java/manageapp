package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.enumfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.InsufficientProfileDataException;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation.UserNutritionProfileValidationException;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.KeyboardTemplates;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditFieldHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.EnumSet;

import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler.*;

/*
 * Абстрактный обработчик изменения полей, со enum-значениями
 *
 */
public abstract class AbstractEnumProfileEditFieldHandler<
        E extends Enum<E> & UserNutritionProfile.Labeled>
        implements ProfileEditFieldHandler {

    protected static final int ELEMENT_PER_ROW = 2;

    protected final CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler;
    protected final UserNutritionProfileService profileService;
    // Полем, а не в конструкторе: иначе пришлось бы менять конструкторы всех наследников
    @Autowired
    protected CalorieTexts texts;

    protected AbstractEnumProfileEditFieldHandler(
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler,
            UserNutritionProfileService profileService
    ) {
        this.callbackHandler = callbackHandler;
        this.profileService = profileService;
    }

    @Override
    public void handle(
            Command command,
            TelegramBot telegramBot,
            UserNutritionProfile profile,
            Update update,
            TelegramUser telegramUser
    ) {
        CallbackQuery query = update.getCallbackQuery();
        Message message = TelegramBot.getCallbackMessage(query);

        Long chatId = message.getChatId();
        Integer messageId = message.getMessageId();
        ProfileEditAction action = extractActionOrNull(query.getData());

        //Проверяем есть ли новые данные для обработки
        //Если нет - отправляем начальное сообщение
        if (checkNewData(query.getData())) {
            String newData = extractEditDataOrNull(query.getData());
            profile = applyValue(
                    profile,
                    Enum.valueOf(getEnumClass(), newData)
            );
            try {
                //Обновляем данные
                profile = patchProfile(profile);
                //Пересчитываем нормы КБЖУ
                profileService.recalculateAndSave(profile);
            } catch (UserNutritionProfileValidationException e) {
                //Недопустимые данные
                telegramBot.sendReturnedMessage(chatId,
                        texts.get(telegramUser.getAppUserId(), BotTextKeys.PROFILE_VALIDATION_ERROR));
            } catch (InsufficientProfileDataException e) {
                return;
            }
        } else {
            AppLanguage language = texts.languageOf(telegramUser.getAppUserId());
            telegramBot.sendEditMessage(
                    chatId,
                    // Заголовок выбора: «Выберите пол»
                    texts.get(language, getProfileAction().getChooseKey()),
                    messageId,
                    KeyboardTemplates.buildEnumKeyboard(
                            command,
                            action,
                            EnumSet.allOf(getEnumClass()),
                            ELEMENT_PER_ROW,
                            value -> texts.get(language, value.getTextKey())
                    )
            );
        }
    }

    /** Enum класс */
    protected abstract Class<E> getEnumClass();

    /** Применение значения */
    protected abstract UserNutritionProfile applyValue(UserNutritionProfile profile, E value);
    protected UserNutritionProfile patchProfile(UserNutritionProfile profile) throws UserNutritionProfileValidationException {
        return profileService.patchProfile(profile);
    }
}
