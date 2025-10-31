package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service;

import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.configurations.CodeVerifierUtil;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.OAuthLink;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.OAuthState;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.repositories.OAuthLinkRepository;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.repositories.OAuthStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkStateService {
    private final OAuthLinkRepository linkRepo;
    private final OAuthStateRepository stateRepo;

    /** Вызываешь из бота при /auth — создаешь одноразовую ссылку */

    public UUID createLink(Long telegramId, int ttlMinutes) {
        OAuthLink oAuthLink = new OAuthLink()
                .setId(UUID.randomUUID())
                .setTelegramId(telegramId)
                .setExpireAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES));


        log.info("Saving oauth link: telegramId={}, id={}", telegramId, oAuthLink.getId());
        oAuthLink = linkRepo.save(oAuthLink);
        log.info("Saved link id={} successfully", oAuthLink.getId());
        return oAuthLink.getId();
    }

    /** На старте веб-флоу: потребляем ссылку и создаем state+verifier */
    @Transactional
    public ConsumedLink consumeLinkAndMakeState(UUID linkId) {
        OAuthLink link = linkRepo.findById(linkId)
                .filter(l -> l.getExpireAt().isAfter(Instant.now()))
                .orElseThrow(() -> new IllegalStateException("Link is invalid or expired"));

//        linkRepo.deleteById(linkId); // одноразово

        String verifier = CodeVerifierUtil.generateVerifier();
        OAuthState state = new OAuthState()
                .setId(UUID.randomUUID())
                .setTelegramId(link.getTelegramId())
                .setVerifier(verifier)
                .setExpireAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                ;
        stateRepo.saveAndFlush(state);

        return new ConsumedLink(state.getId(), verifier, link.getTelegramId());
    }

    /** В колбэке: получить и атомарно удалить state */
    @Transactional
    public CallbackState consumeState(UUID stateId) {
        Optional<OAuthState> opt = stateRepo.findById(stateId)
                .filter(s -> s.getExpireAt().isAfter(Instant.now()));
        if (opt.isEmpty()) throw new IllegalStateException("State invalid/expired");

        OAuthState s = opt.get();
        stateRepo.deleteById(stateId); // одноразово
        return new CallbackState(s.getTelegramId(), s.getVerifier());
    }

    public record ConsumedLink(UUID state, String verifier, long telegramId) { }
    public record CallbackState(long telegramId, String verifier) { }
}
