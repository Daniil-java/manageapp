package com.kuklin.manageapp.bots.deparrbot.services;

import com.kuklin.manageapp.bots.deparrbot.models.FlightCode;
import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;
import com.kuklin.manageapp.bots.deparrbot.providers.FlightInfoProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Опрашивает источники по порядку (@Order на провайдерах), пока один не найдёт рейс.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlightInfoService {
    private final List<FlightInfoProvider> providers;

    public FlightDto findFlightOrNull(FlightCode code) {
        for (FlightInfoProvider provider : providers) {
            FlightDto dto = provider.findFlightOrNull(code);
            if (dto != null) {
                log.debug("Рейс {} найден через {}", code.getDisplayName(), provider.getProviderName());
                return dto;
            }
        }
        log.info("Рейс {} не найден ни в одном источнике", code.getDisplayName());
        return null;
    }
}
