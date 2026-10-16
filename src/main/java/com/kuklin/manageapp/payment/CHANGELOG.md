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