package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service;

import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.configurations.GoogleOAuthProperties;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.models.TokenRefreshException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthHttpClient {
    private final GoogleOAuthProperties props;
    private final RestClient rest = RestClient.create();

    public TokenResponse exchangeCode(String code, String codeVerifier) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", props.getClientId());
        if (props.getClientSecret() != null && !props.getClientSecret().isBlank()) {
            form.add("client_secret", props.getClientSecret());
        }
        form.add("code", code);
        form.add("redirect_uri", props.getRedirectUri());
        form.add("code_verifier", codeVerifier);

        return postForm(props.getTokenUri(), form, TokenResponse.class);

    }

    public TokenResponse refresh(String refreshToken) throws TokenRefreshException {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", props.getClientId());
        if (props.getClientSecret() != null && !props.getClientSecret().isBlank()) {
            form.add("client_secret", props.getClientSecret());
        }
        form.add("refresh_token", refreshToken);

        try {
            return postForm(props.getTokenUri(), form, TokenResponse.class);
        } catch (RestClientResponseException ex) {
            // HTTP 4xx/5xx - тело может содержать JSON { "error": "invalid_grant", ... }
            String body = ex.getResponseBodyAsString();
            if (isInvalidGrant(body)) {
                log.error("Google invalid grant error! Check auth!");
                throw new TokenRefreshException(TokenRefreshException.Reason.INVALID_GRANT,
                        "Refresh failed: invalid_grant", ex.getRawStatusCode(), body, ex);
            }
            // остальные 4xx/5xx — считаем OTHER
            log.error("HTTP error!", ex);
            throw new TokenRefreshException(TokenRefreshException.Reason.OTHER,
                    "HTTP error on token endpoint", ex.getRawStatusCode(), body, ex);
        } catch (Exception ex) {
            log.error("HTTP error!", ex);
            // всё остальное
            throw new TokenRefreshException(TokenRefreshException.Reason.OTHER,
                    "Unexpected error while calling token endpoint", -1, null, ex);
        }

    }

    public void revoke(String refreshToken) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("token", refreshToken);
        rest.post()
                .uri(props.getRevokeUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    private <T> T postForm(String uri, MultiValueMap<String,String> form, Class<T> type) {
        try {
            return rest.post().uri(uri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(type);
        } catch (RestClientResponseException ex) {
            log.warn("Google POST {} failed: status={} body={}", uri, ex.getRawStatusCode(), ex.getResponseBodyAsString());
            throw ex;
        }
    }

    // вспомогательный метод: проверяем тело ответа на поле error == "invalid_grant"
    private boolean isInvalidGrant(String body) {
        if (body == null || body.isBlank()) return false;
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            if (node.has("error") && "invalid_grant".equals(node.get("error").asText())) return true;
            // иногда Google даёт описание в error_description
        } catch (Exception ignored) {}
        return false;
    }

    public UserInfo getUserInfo(String accessToken) {
        return rest.get()
                .uri(props.getUserinfoUri())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(UserInfo.class);
    }

    public record TokenResponse(
            String access_token,
            Long expires_in,
            String refresh_token,
            String scope,
            String id_token,
            String token_type
    ) { }

    public record UserInfo(
            String sub,
            String email,
            Boolean email_verified,
            String name,
            String given_name,
            String family_name,
            String picture
    ) { }
}
