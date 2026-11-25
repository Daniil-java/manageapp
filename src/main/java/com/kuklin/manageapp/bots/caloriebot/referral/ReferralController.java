package com.kuklin.manageapp.bots.caloriebot.referral;

import com.kuklin.manageapp.bots.caloriebot.referral.telegram.InviteLinks;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Реферальная ссылка для мини-аппа: «Поделиться» открывает выбор чата Telegram с этой ссылкой —
 * та же, что у кнопки «🚀 Переслать другу» в боте, пришедший по ней засчитывается приглашением.
 */
@RestController
@RequestMapping("/calorie/referral")
@RequiredArgsConstructor
@Tag(name = "Приглашения", description = "Реферальная ссылка пользователя")
public class ReferralController {

    private final InviteLinks inviteLinks;

    @GetMapping("/link")
    @Operation(summary = "Реферальная ссылка", description = "https://t.me/<бот>?start=ref<appUserId>; "
            + "запись для учёта переходов создаётся при первом запросе")
    public ReferralLinkDto getReferralLink(@Parameter(hidden = true) @AuthenticationPrincipal Long appUserId) {
        return new ReferralLinkDto(inviteLinks.getReferralUrl(appUserId));
    }

    public record ReferralLinkDto(String url) {
    }
}
