package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service;

import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.configurations.GoogleOAuthProperties;
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

//        return rest.post()
//                .uri(props.getTokenUri())
//                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
//                .body(form)
//                .retrieve()
//                .body(TokenResponse.class);
    }

    public TokenResponse refresh(String refreshToken) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", props.getClientId());
        if (props.getClientSecret() != null && !props.getClientSecret().isBlank()) {
            form.add("client_secret", props.getClientSecret());
        }
        form.add("refresh_token", refreshToken);

        return postForm(props.getTokenUri(), form, TokenResponse.class);
//        return rest.post()
//                .uri(props.getTokenUri())
//                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
//                .body(form)
//                .retrieve()
//                .body(TokenResponse.class);
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
