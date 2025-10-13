package com.kuklin.manageapp.bots.kworkparser.services;

import com.kuklin.manageapp.bots.kworkparser.entities.UserUrl;
import com.kuklin.manageapp.bots.kworkparser.repositories.UserUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserUrlService {
    private final UserUrlRepository userUrlRepository;

    public UserUrl addUserUrl(Long telegramId, Long urlId) {
        Optional<UserUrl> optionalUserUrl = userUrlRepository.findUserUrlByUrlIdAndTelegramId(urlId, telegramId);
        if (optionalUserUrl.isPresent()) {
            return optionalUserUrl.get();
        }
        return userUrlRepository.save(
                new UserUrl()
                        .setUrlId(urlId)
                        .setTelegramId(telegramId)
        );
    }

    public List<Long> getAllUsersIdByUrlId(Long urlId) {
        return userUrlRepository.findAllByUrlId(urlId).stream()
                .map(UserUrl::getTelegramId)
                .collect(Collectors.toList());
    }
}
