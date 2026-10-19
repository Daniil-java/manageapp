package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmAttributionRepository;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmClickRepository;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmLinkRepository;
import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmClick;
import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmLink;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.UtmCalorieUpdateHandler.UtmDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UtmServiceTest {

    private final UtmLinkRepository linkRepository = mock(UtmLinkRepository.class);
    private final UtmClickRepository clickRepository = mock(UtmClickRepository.class);
    private final UtmAttributionRepository attributionRepository = mock(UtmAttributionRepository.class);
    private final UtmService service = new UtmService(linkRepository, clickRepository, attributionRepository);

    private UtmLink givenLink(long id, String code, UtmLink.OwnerType ownerType, Long creatorId) {
        UtmLink link = new UtmLink().setId(id).setCode(code).setOwnerType(ownerType).setCreatorId(creatorId);
        when(linkRepository.findByCode(code)).thenReturn(Optional.of(link));
        return link;
    }

    @Test
    void newUserByReferralLinkRemembersReferrer() {
        givenLink(5, "ref7", UtmLink.OwnerType.USER, 7L);

        service.processClick("ref7", 100L, true);

        ArgumentCaptor<UtmClick> click = ArgumentCaptor.forClass(UtmClick.class);
        verify(clickRepository).save(click.capture());
        assertThat(click.getValue().isNewUser()).isTrue();
        assertThat(click.getValue().getUtmLinkId()).isEqualTo(5L);
        verify(attributionRepository).insertIfAbsent(100L, 5L, 7L);
    }

    @Test
    void newUserByChannelLinkHasNoReferrer() {
        givenLink(6, "blogger_oleg", UtmLink.OwnerType.TG_CHANNEL, 1L);

        service.processClick("blogger_oleg", 100L, true);

        verify(attributionRepository).insertIfAbsent(eq(100L), eq(6L), isNull());
    }

    @Test
    void adminCreatedUserLinkIsNotReferral() {
        // Тип «Пользователь» из админской формы: creatorId — админ, а не пригласивший
        givenLink(8, "blogger_ivan", UtmLink.OwnerType.USER, 1L);

        service.processClick("blogger_ivan", 100L, true);

        verify(attributionRepository).insertIfAbsent(eq(100L), eq(8L), isNull());
    }

    @Test
    void oldUserClickIsCountedButNotAttributed() {
        givenLink(5, "ref7", UtmLink.OwnerType.USER, 7L);

        service.processClick("ref7", 100L, false);

        ArgumentCaptor<UtmClick> click = ArgumentCaptor.forClass(UtmClick.class);
        verify(clickRepository).save(click.capture());
        assertThat(click.getValue().isNewUser()).isFalse();
        verify(attributionRepository, never()).insertIfAbsent(anyLong(), anyLong(), any());
    }

    @Test
    void ownReferralLinkIsIgnored() {
        givenLink(5, "ref7", UtmLink.OwnerType.USER, 7L);

        service.processClick("ref7", 7L, true);

        verify(clickRepository, never()).save(any());
        verify(attributionRepository, never()).insertIfAbsent(anyLong(), anyLong(), any());
    }

    @Test
    void unknownCodeIsIgnored() {
        when(linkRepository.findByCode(anyString())).thenReturn(Optional.empty());

        service.processClick("nope", 100L, true);

        verify(clickRepository, never()).save(any());
        verify(attributionRepository, never()).insertIfAbsent(anyLong(), anyLong(), any());
    }

    @Test
    void codeProblems() {
        when(linkRepository.findByCode(anyString())).thenReturn(Optional.empty());
        givenLink(9, "taken_code", UtmLink.OwnerType.TG_CHANNEL, 1L);

        assertThat(service.getCodeProblemOrNull("blogger_oleg-2")).isNull();
        assertThat(service.getCodeProblemOrNull("ab")).isEqualTo(UtmService.CodeProblem.INVALID);
        assertThat(service.getCodeProblemOrNull("a".repeat(33))).isEqualTo(UtmService.CodeProblem.INVALID);
        assertThat(service.getCodeProblemOrNull("канал")).isEqualTo(UtmService.CodeProblem.INVALID);
        assertThat(service.getCodeProblemOrNull("with space")).isEqualTo(UtmService.CodeProblem.INVALID);
        assertThat(service.getCodeProblemOrNull("ref123")).isEqualTo(UtmService.CodeProblem.RESERVED);
        assertThat(service.getCodeProblemOrNull("zch5")).isEqualTo(UtmService.CodeProblem.RESERVED);
        assertThat(service.getCodeProblemOrNull("p_promo")).isEqualTo(UtmService.CodeProblem.RESERVED);
        assertThat(service.getCodeProblemOrNull("taken_code")).isEqualTo(UtmService.CodeProblem.TAKEN);
    }

    @Test
    void normalizeCode() {
        assertThat(UtmService.normalizeCode("  Blogger_Oleg ")).isEqualTo("blogger_oleg");
        assertThat(UtmService.normalizeCode("   ")).isNull();
        assertThat(UtmService.normalizeCode(null)).isNull();
    }

    @Test
    void saveLinkKeepsOwnerTypeAndCustomCode() {
        when(linkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UtmLink link = service.saveLink(
                new UtmDto("WEB", "Blogger_Oleg", "Олег", "https://oleg.example", " ", null), 1L);

        assertThat(link.getCode()).isEqualTo("blogger_oleg");
        assertThat(link.getOwnerType()).isEqualTo(UtmLink.OwnerType.WEB);
        assertThat(link.getDescription()).isNull();
    }

    @Test
    void saveLinkGeneratesCodeAndFallsBackToChannel() {
        when(linkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UtmLink link = service.saveLink(new UtmDto("???", "", "Канал", "@chan", "опт", null), 1L);

        assertThat(link.getCode()).startsWith("partner_").hasSize("partner_".length() + 8);
        assertThat(link.getOwnerType()).isEqualTo(UtmLink.OwnerType.TG_CHANNEL);
        assertThat(link.getDescription()).isEqualTo("опт");
    }
}
