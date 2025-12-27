package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service;

import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.configurations.CodeVerifierUtil;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.OAuthLink;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.OAuthState;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.repositories.OAuthLinkRepository;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.repositories.OAuthStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LinkStateService {
    private final OAuthLinkRepository linkRepo;
    private final OAuthStateRepository stateRepo;

    /** Вызываешь из бота при /auth — создаешь одноразовую ссылку */
    @Transactional
    public UUID createLink(long chatId, int ttlMinutes) {
        var link = OAuthLink.builder()
                .id(UUID.randomUUID())
                .chatId(chatId)
                .expireAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES))
                .build();
        linkRepo.save(link);
        return link.getId();
    }

    /** На старте веб-флоу: потребляем ссылку и создаем state+verifier */
    @Transactional
    public ConsumedLink consumeLinkAndMakeState(UUID linkId) {
        var link = linkRepo.findById(linkId)
                .filter(l -> l.getExpireAt().isAfter(Instant.now()))
                .orElseThrow(() -> new IllegalStateException("Link is invalid or expired"));

        linkRepo.deleteById(linkId); // одноразово

        String verifier = CodeVerifierUtil.generateVerifier();
        var state = OAuthState.builder()
                .id(UUID.randomUUID())
                .chatId(link.getChatId())
                .verifier(verifier)
                .expireAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
        stateRepo.save(state);

        return new ConsumedLink(state.getId(), verifier, link.getChatId());
    }

    /** В колбэке: получить и атомарно удалить state */
    @Transactional
    public CallbackState consumeState(UUID stateId) {
        var opt = stateRepo.findById(stateId)
                .filter(s -> s.getExpireAt().isAfter(Instant.now()));
        if (opt.isEmpty()) throw new IllegalStateException("State invalid/expired");

        var s = opt.get();
        stateRepo.deleteById(stateId); // одноразово
        return new CallbackState(s.getChatId(), s.getVerifier());
    }

    public record ConsumedLink(UUID state, String verifier, long chatId) { }
    public record CallbackState(long chatId, String verifier) { }
}
