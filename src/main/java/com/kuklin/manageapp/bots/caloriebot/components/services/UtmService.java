package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmClickRepository;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmLinkRepository;
import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmClick;
import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmLink;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.UtmCalorieUpdateHandler;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UtmService {

    public static final String CHANNEL_POST_CODE_PREFIX = "zch";

    private final UtmLinkRepository utmLinkRepository;
    private final UtmClickRepository utmClickRepository;
    // Предположим, у тебя есть сервис для работы с юзерами, чтобы понять, новый он или нет
    private final TelegramUserService telegramUserService;

    @Transactional
    public boolean processClick(String code, Long userId) {
        return utmLinkRepository.findByCode(code).map(link -> {
            // Проверяем, был ли этот пользователь РАНЕЕ в таблице кликов
            boolean isFirstUtmClick = !utmClickRepository.existsByUserId(userId);

            UtmClick click = new UtmClick()
                    .setUtmLinkId(link.getId())
                    .setUserId(userId)
                    .setNewUser(isFirstUtmClick)
                    ;

            utmClickRepository.save(click);
            log.info("Registered click for link: {}, user: {}, firstTime: {}", code, userId, isFirstUtmClick);

            return isFirstUtmClick; // Это вернется в .map()
        }).orElse(false); // Если ссылка не найдена, вернем false
    }

    public String getOrCreateReferralLink(Long userId) {
        String code = "ref" + userId; // Простой и понятный код
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

    /** Переходы в бота по ссылкам из постов канала: всего и впервые по UTM-ссылке. */
    public ChannelClicks countChannelClicksSince(Instant since) {
        return new ChannelClicks(
                utmClickRepository.countByCodePrefixSince(CHANNEL_POST_CODE_PREFIX + "%", since),
                utmClickRepository.countNewByCodePrefixSince(CHANNEL_POST_CODE_PREFIX + "%", since));
    }

    public record ChannelClicks(long clicks, long firstTime) {
    }

    public UtmLink saveLink(UtmCalorieUpdateHandler.UtmDto utmDto, Long creatorId) {
        // 2. Сгенерировать уникальный UTM-код
        String utmCode = "partner_" + UUID.randomUUID().toString().substring(0, 8);
        String desc = utmDto.ownerType() + "\n" + utmDto.tittle() + "\n" + utmDto.sourceUrl() + "\n" + utmDto.description();
        UtmLink utmLink = new UtmLink()
                .setOwnerType(UtmLink.OwnerType.TG_CHANNEL)
                .setTittle(utmDto.tittle())
                .setSourceUrl(utmDto.sourceUrl())
                .setDescription(desc)
                .setCreatorId(creatorId)
                .setCode(utmCode)
                ;

        return utmLinkRepository.save(utmLink);
    }
}
