package com.kuklin.manageapp.bots.kworkparser.services;

import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.kworkparser.entities.Kwork;
import com.kuklin.manageapp.bots.kworkparser.entities.UserKworkNotification;
import com.kuklin.manageapp.bots.kworkparser.repositories.UserKworkNotificationRepository;
import com.kuklin.manageapp.bots.kworkparser.telegram.KworkParserTelegramBot;
import com.kuklin.manageapp.common.library.tgutils.ThreadUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserKworkNotificationService {
    private final UserKworkNotificationRepository userKworkNotificationRepository;
    private final OpenAiProviderProcessor openAiIntegrationService;
    private final KworkParserTelegramBot kworkParserTelegramBot;
//    private static final String AI_REQUEST =
//            """
//                    Ты получаешь объект в текстовом виде (с полями name, description, priceLimit, maxDays, username, даты и т.д.). \s
//                                                       Твоя задача — всегда преобразовать его в шаблонное сообщение для чата.
//
//                                                       Правила:
//                                                       1. В начале сообщения напиши **название задания**. \s
//                                                       2. Затем дай **краткое понятное описание** (перескажи суть description человеческим языком, выдели специфику). \s
//                                                       3. Далее укажи **цену** (priceLimit), сделав формулировку понятной: «Цена: … руб.» или «Бюджет до …». \s
//                                                       4. После этого перечисли **дополнительные сведения** в одном абзаце или короткими строками:
//                                                          - срок выполнения (maxDays, в днях);
//                                                          - автор (username и id, если есть);
//                                                          - файлы (есть / нет);
//                                                          - статус (status);
//                                                          - даты: создано, активно с, истекает (кратко, в формате «Создано: … / Активно: … / Истекает: …»).
//
//                                                       Формат должен быть единый, понятный и лаконичный. \s
//                                                       Не добавляй ничего от себя. Если поле пустое — пиши «—». \s
//                                                       Сообщение должно быть готовым к отправке в чат.
//
//                                                       Текст для обработки: %s
//                    """;

    public void notificate(List<Long> telegramIds, List<Kwork> kworks) {
        for (Long telegramId : telegramIds) {

            for (Kwork kwork : kworks) {

                Optional<UserKworkNotification> optional =
                        userKworkNotificationRepository
                                .findUserKworkNotificationByKworkIdAndTelegramIdAndStatus(kwork.getKworkId(), telegramId, UserKworkNotification.Status.SENT);

                if (optional.isEmpty()) {
                    UserKworkNotification notification = new UserKworkNotification()
                            .setKworkId(kwork.getKworkId())
                            .setTelegramId(telegramId)
                            .setStatus(UserKworkNotification.Status.CREATED)
                            ;

//                    String response = openAiIntegrationService.fetchResponse(String.format(AI_REQUEST,kwork.toString()));
                    Message message = kworkParserTelegramBot.sendReturnedMessage(telegramId, kwork.toString());
                    ThreadUtil.sleep(100);
                    if (message != null) {
                        notification.setStatus(UserKworkNotification.Status.SENT);
                    } else {
                        notification.setStatus(UserKworkNotification.Status.ERROR);
                    }
                    userKworkNotificationRepository.save(notification);
                }
            }
        }
    }
}
