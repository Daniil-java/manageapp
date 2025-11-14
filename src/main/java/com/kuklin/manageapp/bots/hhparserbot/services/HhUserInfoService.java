package com.kuklin.manageapp.bots.hhparserbot.services;

import com.kuklin.manageapp.bots.hhparserbot.entities.HhUserInfo;
import com.kuklin.manageapp.bots.hhparserbot.repositories.HhUserInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HhUserInfoService {
    private final HhUserInfoRepository hhUserInfoRepository;

    public HhUserInfo save(HhUserInfo userEntity) {
        return hhUserInfoRepository.save(userEntity);
    }

    public HhUserInfo setInfo(Long telegramId, String info) {
        HhUserInfo hhUserInfo = getHhUserInfoByTelegramIdOrCreate(telegramId);
        return hhUserInfoRepository.save(hhUserInfo.setInfo(info));
    }

    public HhUserInfo getHhUserInfoByTelegramIdOrCreate(Long telegramId) {
        Optional<HhUserInfo> optional = hhUserInfoRepository.findByTelegramId(telegramId);

        if (optional.isPresent()) {
            return optional.get();
        } else {
            return hhUserInfoRepository.save(
                    new HhUserInfo()
                            .setTelegramId(telegramId)
            );
        }
    }

    public HhUserInfo getHhUserInfoByIdOrNull(Long id) {
        return hhUserInfoRepository.findById(id).orElse(null);
    }
}
