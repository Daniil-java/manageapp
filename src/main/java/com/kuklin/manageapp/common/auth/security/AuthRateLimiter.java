package com.kuklin.manageapp.common.auth.security;

import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.common.configurations.AuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Лимиты на /auth/*: защита от массовой регистрации, перебора паролей и нагрузки на BCrypt
 * (одна проверка пароля — ~0,25 с процессора).
 * Значения — {@link AuthProperties.RateLimit} (application.yaml, auth.rate-limit):
 * <ul>
 * <li>по IP — сколько раз можно регистрироваться / входить по паролю / входить через Telegram;</li>
 * <li>по email — сколько неверных паролей: лимит по IP обходится сменой IP (прокси),
 * а этот — нет, он считает попытки к одному аккаунту с любых IP.</li>
 * </ul>
 * При превышении — 429 AUTH_RATE_LIMIT с указанием, через сколько повторить.
 * <p>
 * Кто вызывает: только {@link AuthService} — register, login, loginByTelegram.
 * <p>
 * Как устроено: «скользящее окно», как в AiRateLimiter. На каждый ключ (действие + IP или email)
 * храним очередь моментов времени — когда было событие. Старые (вышедшие из окна) выкидываем,
 * оставшиеся считаем: если их уже max — отказ. Например, лимит 5 в час: разрешено, если за последние
 * 60 минут было меньше 5 регистраций с этого IP, — без «сброса в начале часа», окно едет вместе со временем.
 * <p>
 * Хранится в памяти: работает, пока бэк в одном экземпляре; после перезапуска счётчики обнуляются.
 */
@Slf4j
@Component
public class AuthRateLimiter {

    /** Что ограничиваем по IP — у каждого действия свой лимит и свой счётчик. */
    public enum Action {REGISTER, LOGIN, TELEGRAM}

    // Префикс ключа для счётчика неверных паролей (ключ — email, а не IP)
    private static final String FAILED_PASSWORD = "FAILED_PASSWORD";

    private final AuthProperties.RateLimit limits;
    private final Clock clock;

    // Все счётчики в одной карте. Ключ — "ДЕЙСТВИЕ:IP" ("LOGIN:1.2.3.4") или "FAILED_PASSWORD:email".
    // Значение — моменты событий по возрастанию: самые старые в начале очереди, новые — в конце.
    // ConcurrentHashMap — запросы приходят из разных потоков одновременно
    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();

    @Autowired
    public AuthRateLimiter(AuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    // Для тестов — подменяем часы, чтобы «промотать» время без sleep
    AuthRateLimiter(AuthProperties properties, Clock clock) {
        this.limits = properties.getRateLimit();
        this.clock = clock;
    }

    /**
     * Засчитывает попытку с этого IP или бросает 429, если лимит исчерпан.
     * Считаются все попытки — и удачные, и нет: цель — ограничить частоту запросов, а не только ошибки.
     * <p>
     * Вызывается в начале register / login / loginByTelegram — до проверки пароля или подписи Telegram,
     * чтобы при флуде не тратить на них процессор.
     */
    public void acquireIpOrThrow(Action action, String ip) {
        AuthProperties.Limit limit = limitFor(action);
        long retryAfter = tryAcquire(action.name() + ":" + ip, limit);
        if (retryAfter > 0) {
            // IP в логе — чтобы после деплоя проверить, что видим настоящий IP клиента, а не прокси
            log.warn("Auth rate limit: {} from IP {} ({} per {})", action, ip, limit.getMax(), limit.getWindow());
            throw tooMany(retryAfter);
        }
    }

    /**
     * Бросает 429, если для email за последние window уже max неверных паролей.
     * Попытку НЕ засчитывает — засчитывает только {@link #recordFailedPassword}, когда пароль оказался неверным.
     * <p>
     * Вызывается в login до проверки пароля: заблокированный аккаунт не перебирают дальше даже правильным паролем
     * (иначе атакующий узнал бы, что угадал). Блокировка снимается сама, когда старые ошибки выпадут из окна.
     */
    public void checkEmailNotLockedOrThrow(String email) {
        AuthProperties.Limit limit = limits.getFailedPasswordPerEmail();
        Deque<Instant> failures = events.get(emailKey(email));
        // Ошибок по этому email не было (или их уже вычистили) — не заблокирован
        if (failures == null) return;

        Instant now = clock.instant();
        // synchronized на очередь этого ключа: параллельные запросы к одному email не испортят очередь,
        // а запросы к разным email друг друга не ждут
        synchronized (failures) {
            removeOlderThan(failures, now.minus(limit.getWindow()));
            if (failures.size() >= limit.getMax()) {
                // email в лог не пишем — персональные данные
                log.warn("Auth: email locked after {} failed passwords", limit.getMax());
                throw tooMany(retryAfterSeconds(failures, limit, now));
            }
        }
    }

    /**
     * Неверный пароль или несуществующий email — засчитываем ошибку на этот email.
     * Несуществующий тоже считаем, чтобы по поведению лимита нельзя было понять, есть ли такой аккаунт.
     */
    public void recordFailedPassword(String email) {
        Deque<Instant> failures = events.computeIfAbsent(emailKey(email), k -> new ArrayDeque<>());
        synchronized (failures) {
            failures.addLast(clock.instant());
        }
    }

    /** Удачный вход — счётчик неверных паролей для email обнуляется (владелец ошибся пару раз и вспомнил). */
    public void resetFailedPasswords(String email) {
        events.remove(emailKey(email));
    }

    /**
     * Раз в 10 минут убираем ключи, у которых не осталось событий в пределах самого длинного окна (1 час).
     * Без этого карта росла бы бесконечно: каждый новый IP и каждый перебираемый email — новый ключ.
     */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void evictIdle() {
        Instant border = clock.instant().minus(longestWindow());
        events.entrySet().removeIf(entry -> {
            Deque<Instant> deque = entry.getValue();
            synchronized (deque) {
                removeOlderThan(deque, border);
                return deque.isEmpty();
            }
        });
    }

    // --- Private ---

    /**
     * Сердце лимитера: проверить окно и, если есть место, засчитать событие.
     * Проверка и добавление — под одним synchronized, поэтому два параллельных запроса
     * не могут оба увидеть «осталось одно место» и оба пройти.
     *
     * @return 0 — разрешено (событие засчитано), иначе через сколько секунд освободится место
     */
    private long tryAcquire(String key, AuthProperties.Limit limit) {
        Instant now = clock.instant();
        // Первый запрос с этого IP — создаём пустую очередь
        Deque<Instant> deque = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            // 1. Выкидываем события старше окна — они больше не считаются
            removeOlderThan(deque, now.minus(limit.getWindow()));
            // 2. В окне уже max событий — отказ, считаем, когда освободится место
            if (deque.size() >= limit.getMax()) {
                return retryAfterSeconds(deque, limit, now);
            }
            // 3. Место есть — засчитываем текущее событие
            deque.addLast(now);
            return 0;
        }
    }

    /** Лимит для действия из application.yaml (auth.rate-limit.register / login / telegram). */
    private AuthProperties.Limit limitFor(Action action) {
        return switch (action) {
            case REGISTER -> limits.getRegister();
            case LOGIN -> limits.getLogin();
            case TELEGRAM -> limits.getTelegram();
        };
    }

    /** Самое длинное окно из всех лимитов — события старше него точно ни на что не влияют. */
    private Duration longestWindow() {
        return Stream.of(limits.getRegister(), limits.getLogin(), limits.getTelegram(), limits.getFailedPasswordPerEmail())
                .map(AuthProperties.Limit::getWindow)
                .max(Duration::compareTo)
                .orElse(Duration.ofHours(1));
    }

    /** Ключ счётчика неверных паролей. Регистр и пробелы убираем — "User@Mail.com " и "user@mail.com" один аккаунт. */
    private static String emailKey(String email) {
        return FAILED_PASSWORD + ":" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Через сколько секунд освободится место. В окне ровно max (или больше) событий; место появится,
     * когда из окна выпадет самое раннее из последних max. Пример: лимит 5 в час, самое раннее из последних
     * пяти было в 10:20 — повторить можно в 11:20.
     */
    private static long retryAfterSeconds(Deque<Instant> deque, AuthProperties.Limit limit, Instant now) {
        Instant oldestCounted = deque.stream()
                .skip(Math.max(0, deque.size() - limit.getMax()))
                .findFirst()
                .orElse(now);
        Instant freesAt = oldestCounted.plus(limit.getWindow());
        // Округляем вверх и минимум 1 секунда — чтобы не ответить «повторите через 0 секунд»
        return Math.max(1, (Duration.between(now, freesAt).toMillis() + 999) / 1000);
    }

    /**
     * Выкидывает из начала очереди события не позже границы.
     * Очередь отсортирована по времени, поэтому старые всегда в начале — дальше первого свежего не идём.
     */
    private static void removeOlderThan(Deque<Instant> deque, Instant border) {
        while (!deque.isEmpty() && !deque.peekFirst().isAfter(border)) {
            deque.pollFirst();
        }
    }

    /** 429 AUTH_RATE_LIMIT; текст уходит клиенту как есть — фронт показывает его пользователю. */
    private static ErrorResponseException tooMany(long retryAfterSeconds) {
        return new ErrorResponseException(ErrorStatus.AUTH_RATE_LIMIT,
                String.format("Too many attempts. Try again in %s.", formatWait(retryAfterSeconds)));
    }

    /** 40 → "1 min", 3700 → "1 h 2 min". */
    private static String formatWait(long seconds) {
        long minutes = (seconds + 59) / 60;
        if (minutes < 60) return minutes + " min";
        return minutes / 60 + " h " + minutes % 60 + " min";
    }
}
