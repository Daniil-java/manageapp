package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmAttributionRepository;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmClickRepository;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmLinkRepository;
import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmClick;
import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmLink;
import com.kuklin.manageapp.bots.caloriebot.models.landing.CalculatorStartParam;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.UtmCalorieUpdateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UtmService {

    public static final String CHANNEL_POST_CODE_PREFIX = "zch";
    public static final String REFERRAL_CODE_PREFIX = "ref";
    /** Свой код ссылки из админской формы. Калькулятор лендинга дописывает его к p_..., а start Telegram — до 64 символов. */
    public static final int CODE_MIN_LENGTH = 3;
    public static final int CODE_MAX_LENGTH = 32;
    /** Префиксы кодов, которые бот создаёт сам, — вручную такие коды не создаются. */
    private static final List<String> RESERVED_CODE_PREFIXES =
            List.of(REFERRAL_CODE_PREFIX, CHANNEL_POST_CODE_PREFIX, CalculatorStartParam.PREFIX + "_");

    private final UtmLinkRepository utmLinkRepository;
    private final UtmClickRepository utmClickRepository;
    private final UtmAttributionRepository utmAttributionRepository;

    /**
     * Переход в бота по UTM-ссылке (/start с кодом).
     * <p>
     * Новому пользователю ссылка запоминается как источник (utm_attributions), по реферальной — и кто пригласил.
     * Переход по своей реферальной ссылке не учитывается.
     *
     * @param newUser пользователь впервые запустил бота (StartUpdateHandler: ему только что выдан пробный период)
     */
    @Transactional
    public void processClick(String code, Long appUserId, boolean newUser) {
        UtmLink link = utmLinkRepository.findByCode(code).orElse(null);
        if (link == null) {
            log.info("Unknown UTM code: {}, user: {}", code, appUserId);
            return;
        }
        Long referrerAppUserId = getReferrerAppUserIdOrNull(link);
        if (appUserId.equals(referrerAppUserId)) {
            log.info("Own referral link ignored: {}, user: {}", code, appUserId);
            return;
        }

        utmClickRepository.save(new UtmClick()
                .setUtmLinkId(link.getId())
                .setUserId(appUserId)
                .setNewUser(newUser));
        if (newUser) {
            utmAttributionRepository.insertIfAbsent(appUserId, link.getId(), referrerAppUserId);
        }
        log.info("Registered click for link: {}, user: {}, newUser: {}", code, appUserId, newUser);
    }

    /**
     * Реферальная ссылка — ref<appUserId>, её владелец и есть пригласивший. Ссылки типа «Пользователь»
     * из админской формы (например, блогера) реферальными не считаются: creatorId у них — админ.
     */
    private static Long getReferrerAppUserIdOrNull(UtmLink link) {
        boolean referral = link.getOwnerType() == UtmLink.OwnerType.USER
                && link.getCode().equals(REFERRAL_CODE_PREFIX + link.getCreatorId());
        return referral ? link.getCreatorId() : null;
    }

    public String getOrCreateReferralLink(Long userId) {
        String code = REFERRAL_CODE_PREFIX + userId; // Простой и понятный код
        // Код детерминирован — строка нужна только для учёта кликов (processClick). Двойное нажатие
        // «пригласить» не должно падать на UNIQUE(code), поэтому INSERT ... ON CONFLICT DO NOTHING.
        // Сначала find: ON CONFLICT тратит значение id-последовательности даже без вставки.
        if (utmLinkRepository.findByCode(code).isEmpty()) {
            utmLinkRepository.insertIfAbsent(code, UtmLink.OwnerType.USER.name(), userId);
        }
        return code;
    }

    /**
     * Ссылка на бота из поста канала: код zch<id поста>. По ней в отчёте канала видно, сколько людей пришло.
     */
    public String getOrCreateChannelPostCode(Long postId) {
        String code = CHANNEL_POST_CODE_PREFIX + postId;
        if (utmLinkRepository.findByCode(code).isEmpty()) {
            utmLinkRepository.insertIfAbsent(code, UtmLink.OwnerType.TG_CHANNEL.name(), null);
        }
        return code;
    }

    /** Переходы в бота по ссылкам из постов канала: всего и из них новых пользователей (впервые запустили бота). */
    public ChannelClicks countChannelClicksSince(Instant since) {
        return new ChannelClicks(
                utmClickRepository.countByCodePrefixSince(CHANNEL_POST_CODE_PREFIX + "%", since),
                utmClickRepository.countNewByCodePrefixSince(CHANNEL_POST_CODE_PREFIX + "%", since));
    }

    public record ChannelClicks(long clicks, long newUsers) {
    }

    /** Код из формы: без пробелов по краям и в нижнем регистре. Пусто — null, код сгенерируется. */
    public static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toLowerCase(Locale.ROOT);
    }

    /** Чем плох код ссылки из админской формы (уже после normalizeCode), или null — годится. */
    public CodeProblem getCodeProblemOrNull(String code) {
        boolean validChars = code.chars()
                .allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-');
        if (!validChars || code.length() < CODE_MIN_LENGTH || code.length() > CODE_MAX_LENGTH) {
            return CodeProblem.INVALID;
        }
        if (RESERVED_CODE_PREFIXES.stream().anyMatch(code::startsWith)) {
            return CodeProblem.RESERVED;
        }
        if (utmLinkRepository.findByCode(code).isPresent()) {
            return CodeProblem.TAKEN;
        }
        return null;
    }

    public enum CodeProblem {
        INVALID, RESERVED, TAKEN
    }

    /**
     * Ссылка из админской формы. Код — свой (проверен getCodeProblemOrNull) или partner_<8 символов UUID>.
     * Тип не из списка — «ТГ-канал».
     */
    public UtmLink saveLink(UtmCalorieUpdateHandler.UtmDto utmDto, Long creatorId) {
        String code = normalizeCode(utmDto.code());
        String description = utmDto.description() == null || utmDto.description().isBlank()
                ? null
                : utmDto.description();
        UtmLink utmLink = new UtmLink()
                .setOwnerType(getOwnerType(utmDto.ownerType()))
                .setTittle(utmDto.tittle())
                .setSourceUrl(utmDto.sourceUrl())
                .setDescription(description)
                .setCreatorId(creatorId)
                .setCode(code != null ? code : "partner_" + UUID.randomUUID().toString().substring(0, 8));

        return utmLinkRepository.save(utmLink);
    }

    private static UtmLink.OwnerType getOwnerType(String ownerType) {
        for (UtmLink.OwnerType type : UtmLink.OwnerType.values()) {
            if (type.name().equals(ownerType)) {
                return type;
            }
        }
        return UtmLink.OwnerType.TG_CHANNEL;
    }
}
