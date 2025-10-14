package com.kuklin.manageapp.bots.hhparserbot.services;

import com.kuklin.manageapp.bots.hhparserbot.entities.HhUserInfo;
import com.kuklin.manageapp.bots.hhparserbot.entities.WorkFilter;
import com.kuklin.manageapp.bots.hhparserbot.models.HhSimpleResponseDto;
import com.kuklin.manageapp.bots.hhparserbot.repositories.WorkFilterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HhWorkFilterService {
    private final WorkFilterRepository workFilterRepository;
    private final HhApiService hhApiService;

    public WorkFilter create(HhUserInfo hhUserInfo, String url) {
        return workFilterRepository.findByHhUserInfoIdAndUrl(hhUserInfo.getId(), url)
                .orElseGet(() -> workFilterRepository.save(
                        new WorkFilter()
                                .setHhUserInfoId(hhUserInfo.getId())
                                .setUrl(url)
                        )
                );
    }

    public List<WorkFilter> getAllByUserId(long hhUserInfoId) {
        return workFilterRepository.findAllByHhUserInfoId(hhUserInfoId);
    }

    public List<WorkFilter> saveAll(Long telegramId, List<String> urlList) {
        List<WorkFilter> list = new ArrayList<>();
        HhUserInfo user = new HhUserInfo().setTelegramId(telegramId);
        for (String url: urlList) {
            list.add(new WorkFilter()
                    .setHhUserInfoId(user.getId())
                    .setUrl(url));
        }
        return workFilterRepository.saveAll(list);
    }

    public List<WorkFilter> getAll() {
        return workFilterRepository.findAll();
    }

    //Получение ДТО вакансий, по переденной ссылке
    public List<HhSimpleResponseDto> loadHhVacancies(WorkFilter workFilter) {
        return hhApiService.loadAndParseHhVacancies(workFilter);
    }

    public WorkFilter getWorkFilterByIdOrNull(Long id) {
        return workFilterRepository.findById(id).orElse(null);
    }
}
