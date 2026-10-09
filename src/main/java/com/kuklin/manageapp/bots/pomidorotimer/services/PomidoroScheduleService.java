package com.kuklin.manageapp.bots.pomidorotimer.services;

import com.kuklin.manageapp.common.configurations.BotScheduler;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.bots.pomidorotimer.processors.TimerPomidoroScheduleProcessor;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@BotScheduler(BotIdentifier.POMIDORO_BOT)
public class PomidoroScheduleService {
    private final TimerPomidoroScheduleProcessor timerPomidoroScheduleProcessor;

    @Scheduled(cron = "0 * * * * *")
    public void timerBotScheduleProcess() {
        timerPomidoroScheduleProcessor.process();
    }
}
