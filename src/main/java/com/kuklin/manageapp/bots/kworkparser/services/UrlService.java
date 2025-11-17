package com.kuklin.manageapp.bots.kworkparser.services;

import com.kuklin.manageapp.bots.kworkparser.entities.Url;
import com.kuklin.manageapp.bots.kworkparser.repositories.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {
    private final UrlRepository urlRepository;
    private final UserUrlService userUrlService;

    public Url addUrlToUser(String url, Long telegramId) {
        url = url.trim();

        Optional<Url> optionalUrl = urlRepository.findUrlByUrl(url);
        Url resultUrl;

        if (optionalUrl.isEmpty()) {
            resultUrl = urlRepository.save(
                    new Url().setUrl(url));
        } else {
            resultUrl = optionalUrl.get();
        }
        userUrlService.addUserUrl(telegramId, resultUrl.getId());

        return resultUrl;
    }

    public List<Url> getAllUrls() {
        return urlRepository.findAll();
    }

    public Set<String> getAllUrlsString() {
        return urlRepository.findAll()
                .stream()
                .map(Url::getUrl)
                .collect(Collectors.toSet());

    }
}
