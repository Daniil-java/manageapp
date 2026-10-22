## 0.0.5 (22.10.2026)
[et-105] — Цветные кнопки оплаты
* Кнопка оплаты по ссылке (PaymentPayUpdateHandler) и кнопки тарифов (PaymentPlanListUpdateHandler) — зелёные (ButtonStyle.SUCCESS, StyledInlineKeyboardButton из common/library/tgutils). Выбор провайдера — без цвета.

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