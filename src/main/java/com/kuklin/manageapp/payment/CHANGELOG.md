## 0.0.6 (23.10.2026)
[et-106] — Возврат звёзд без нашего участия отменяет покупку
* Fix: при возврате (наш /refund, вебхук ЮKassa) платёж оставался SUCCESS — менялся только providerStatus. Теперь status = REFUNDED: повторное уведомление о возврате больше не отменяет подписку второй раз и не сдвигает очередь. Отчёт по UTM (status = 'SUCCESS') перестаёт считать возвращённые платежи выручкой.
* refunded_payment: telegrambots 6.9 не знает поля и выбрасывал его. RefundedPaymentCapture встраивается в ObjectMapper библиотеки (PartialBotApiMethod.OBJECT_MAPPER, ставится в TelegramBotConfiguration до регистрации ботов): десериализатор Update достаёт message.refunded_payment и запоминает по update_id. Раньше такое сообщение уходило в CALORIE_GENERAL как пустой текст, в платёжном боте — NPE на getText().
* RefundedPaymentUpdateHandler (Command.PAYMENT_REFUNDED, калорийный и платёжный боты) → PaymentService.cancelRefundedTelegramPayment: платёж в звёздах ещё SUCCESS и оплачен этим пользователем — отмена покупки как при /refund. Уже возвращённый (в том числе нашим /refund) не трогаем. TelegramBot.isPaymentUpdate учитывает возврат — обрабатывается, даже когда пользователь занят или пул полон.
* Сверка раз в сутки (StarRefundScheduleProcessor, запуск в SubscriptionSchedulerService в 04:00): getStarTransactions по каждому боту с оплатами в звёздах (StarTransactionsClient). Исходящая транзакция с id платежа — возврат; ещё SUCCESS — отменяем. Подстраховка, если апдейт потерялся.
* Тесты: RefundedPaymentCaptureTest — разбор настоящим GetUpdates. PaymentServiceTelegramRefundTest — отмена один раз, чужой пользователь, неизвестный платёж. StarTransactionsClientTest — исходящие транзакции и страницы.

[et-106] — Снятые с продажи тарифы не продаются
* PricingPlan.isWithdrawnFromSale(): ARCHIVED / DISABLED.
* CommonPaymentFacade.startPlanPayment: по снятому с продажи тарифу (кнопка старого тарифа в чате) счёт не выставляется — PricingPlanNotFoundException, бот отвечает PLAN_NOT_FOUND.
* PaymentService.checkPreCheckoutQuery: счёт, выставленный до того, как тариф сняли с продажи, отклоняется на pre_checkout — до списания звёзд. Уже купленные подписки на такие тарифы не затронуты.
* Тесты: PaymentServicePreCheckoutTest — счёт по ARCHIVED / DISABLED тарифу отклоняется, по AVAILABLE проходит.

## 0.0.5 (22.10.2026)
[et-105] — Цветные кнопки оплаты
* Кнопка оплаты по ссылке (PaymentPayUpdateHandler) и кнопки тарифов (PaymentPlanListUpdateHandler) — зелёные (ButtonStyle.SUCCESS, StyledInlineKeyboardButton из common/library/tgutils). Выбор провайдера — без цвета.
* PaymentPayUpdateHandler: кнопка оплаты собирается через TelegramKeyboard.urlButton вместо ручного InlineKeyboardButton / InlineKeyboardMarkup.

## 0.0.4 (19.10.2026)
[et-102] — Бонусные подписки, события покупки и возврата, /refund только для админов
* /refund <charge_id> (платёжный бот, RefundStartUpdateHandler) — только для payment.admin-telegram-ids (PAYMENT_ADMIN_IDS), остальным бот не отвечает. Раньше команду мог вызвать любой, и отмена в БД шла до возврата в Telegram: с чужим charge_id она отменяла чужую подписку, хотя звёзды не возвращались. Теперь: платёж ищется заранее (PaymentService.getRefundableTelegramPaymentOrNull — звёзды, SUCCESS), звёзды возвращает бот, принявший оплату, — его токеном и на Telegram id плательщика (раньше — токеном платёжного бота и на id вызвавшего, так возврат за калорийного бота не проходил), и только после успешного refundStarPayment платёж отмечается REFUNDED и подписка отменяется. Админ получает итог: ✅, ❌ (в БД ничего не менялось) или ⚠️ (звёзды вернули, а БД не обновилась — править руками).
* UserSubscriptionService.cancelByPayment публикует SubscriptionRefundedEvent (пользователь, бот, платёж). cancelBonusSubscription(id) — отменить бонусную подписку: остаток сгорает, следующие в очереди сдвигаются раньше (общий cancelAndShiftQueue с cancelByPayment).
* PricingPlan.PlanStatus.SYSTEM_BONUS — тариф выдаётся системой как награда и не продаётся. Отдельный статус, а не SYSTEM_FREE: пробный период ищется как первый SYSTEM_FREE тариф бота (getFreePricingPlanOrNull), и второй такой тариф его бы подменил.
* UserSubscriptionService.createBonusSubscription(appUserId, bot, plan, days) — подписка на заданное число дней (может быть меньше срока тарифа), стартует сейчас или после последней активной/запланированной; очередь блокируется, как при покупке. Выдаётся сколько угодно раз — проверяет вызывающий.
* UserSubscriptionService.createSubscriptionByPayment публикует SubscriptionPurchasedEvent (пользователь, бот, платёж) — на него калорийный бот выдаёт награду пригласившему. Публикуется и на продление подписки за звёзды.
* PricingPlanService.getPricingPlanByCodeOrNull — тариф по code_for_order_id.
* Тесты: RefundStartUpdateHandlerTest, UserSubscriptionServiceBonusTest.

## 0.0.3 (15.10.2026)
[et-98] — Повторное списание по одному счёту
* Два списания по одной ссылке (оплата с двух устройств: оба pre_checkout проходят, пока счёт CREATED) больше не сливаются в одну запись. Раньше второй successful_payment выдавал покупку повторно в тот же Payment и затирал charge_id первого — первое списание пропадало из учёта, вернуть его через refundTelegramPayment было нельзя. Теперь счёт берётся под блокировкой (PaymentRepository.findByTelegramInvoicePayloadForUpdate), повтор того же charge_id проверяется уже под ней, а любое ещё одно списание по обработанному счёту записывается отдельным Payment со своим charge_id и выдаётся (PaymentService.createExtraChargePayment; раньше так делалось только для 30-дневной подписки за звёзды — продление Telegram).
* Тесты: PaymentServiceSuccessfulPaymentTest.

## 0.0.2 (14.10.2026)
[et-95] — Повторная оплата счёта, несостоявшиеся счета
* pre_checkout пропускает только платёж в статусе CREATED (PaymentService.checkPreCheckoutQuery). Ссылки на оплату у Telegram многоразовые: раньше уже оплаченный, отменённый или несостоявшийся счёт можно было оплатить ещё раз. Отказ на pre_checkout — до списания звёзд. Автопродление подписки Telegram приходит без pre_checkout и не затронуто.
* PaymentService.markFailed — пометить платёж FAILED (с записью в PaymentFailedLog), если счёт так и не выставлен. changeStatus для FAILED ничего не делал.
* Тесты: PaymentServicePreCheckoutTest.

## 0.0.1 (03.02.2026)
[et-58] - Шедулер подписки
* Реализовал шедулер обновления статуса подписок
* Добавил в сущность UserSubscription - версионирование
* Изменил тип данных времени, в платежной системе, на Instant