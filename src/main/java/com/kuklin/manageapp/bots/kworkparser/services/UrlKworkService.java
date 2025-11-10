package com.kuklin.manageapp.bots.kworkparser.services;

import com.kuklin.manageapp.bots.kworkparser.entities.Kwork;
import com.kuklin.manageapp.bots.kworkparser.entities.UrlKwork;
import com.kuklin.manageapp.bots.kworkparser.repositories.UrlKworkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class UrlKworkService {
    @Autowired
    private UrlKworkRepository urlKworkRepository;
    @Autowired
    @Lazy
    private KworkService kworkService;

    public UrlKwork addUrlKwork(Long urlId, Long kworkId) {
        Optional<UrlKwork> optionalUrlKwork =
                urlKworkRepository.findUrlKworkByUrlIdAndKworkId(urlId, kworkId);

        if (optionalUrlKwork.isPresent()) {
            return optionalUrlKwork.get();
        }

        return urlKworkRepository.save(
                new UrlKwork()
                        .setKworkId(kworkId)
                        .setUrlId(urlId)
        );
    }

    public List<Kwork> findNewKworksByUrlId(Long urlId) {
        List<Long> kworkIds = urlKworkRepository.findAllByUrlId(urlId).stream()
                .map(UrlKwork::getKworkId)
                .toList();

        return kworkService.findAllByIdAndStatus(kworkIds, Kwork.Status.NEW);
    }

}
