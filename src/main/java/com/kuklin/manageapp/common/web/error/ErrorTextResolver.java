package com.kuklin.manageapp.common.web.error;

/**
 * Текст ошибки для поля message в ответе API. Общий код (фильтры, auth) текстов не хранит —
 * их отдаёт модуль, у которого есть словари errors_* (сейчас калорийный бот, CalorieTexts).
 */
public interface ErrorTextResolver {

    String getApiMessage(ErrorResponseException e);
}
