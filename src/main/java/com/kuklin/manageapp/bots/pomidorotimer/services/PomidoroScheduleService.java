package com.kuklin.manageapp.bots.pomidorotimer.services;

import com.kuklin.manageapp.bots.pomidorotimer.processors.TimerPomidoroScheduleProcessor;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class PomidoroScheduleService {
    private final TimerPomidoroScheduleProcessor timerPomidoroScheduleProcessor;

    @Scheduled(cron = "0 * * * * *")
    private void timerBotScheduleProcess() {
        timerPomidoroScheduleProcessor.process();
    }
}
