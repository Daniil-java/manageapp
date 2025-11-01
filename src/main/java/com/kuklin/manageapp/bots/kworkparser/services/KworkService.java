package com.kuklin.manageapp.bots.kworkparser.services;

import com.kuklin.manageapp.bots.kworkparser.entities.Kwork;
import com.kuklin.manageapp.bots.kworkparser.models.KworkDto;
import com.kuklin.manageapp.bots.kworkparser.repositories.KworkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KworkService {
    private final KworkRepository kworkRepository;
    private final UrlKworkService urlKworkService;

    public Kwork saveOrNull(KworkDto kworkDto, Long urlId) {
        return kworkRepository.findById(kworkDto.getId())
                .orElseGet(() -> {
                    Kwork kwork = Kwork.toEntity(kworkDto);

                    Kwork saved = kworkRepository.save(kwork);
                    urlKworkService.addUrlKwork(urlId, saved.getKworkId());
                    return saved;
                });
    }

    public List<Kwork> findAllByIdAndStatus(List<Long> kworkIds, Kwork.Status status) {
        return kworkRepository.findAllByKworkIdInAndStatus(kworkIds, status);
    }
}
