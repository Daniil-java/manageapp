package com.kuklin.manageapp.bots.pomidorotimer.services;

import com.kuklin.manageapp.bots.pomidorotimer.entities.Task;
import com.kuklin.manageapp.bots.pomidorotimer.entities.Timer;
import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import com.kuklin.manageapp.bots.pomidorotimer.models.TimerDto;
import com.kuklin.manageapp.bots.pomidorotimer.models.mappers.TimerMapper;
import com.kuklin.manageapp.bots.pomidorotimer.models.timer.TimerStatus;
import com.kuklin.manageapp.bots.pomidorotimer.repositories.TimerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.kuklin.manageapp.bots.hhparserbot.models.VacancyStatus.PENDING;
import static com.kuklin.manageapp.bots.pomidorotimer.models.timer.TimerStatus.PAUSED;


@Service
@RequiredArgsConstructor
@Slf4j
public class TimerService {
    private final TaskService taskService;
    private final PomidoroUserService pomidoroUserService;
    private final TimerRepository timerRepository;
    private final TimerMapper timerMapper;

    public List<TimerDto> getTimersDtoByUserId(Long userId) {
        return timerMapper.entityListToDtoList(timerRepository.findAllByUserEntityIdAndStatus(userId, TimerStatus.COMPLETE));
    }

    public List<Timer> getAnyNotCompleteTimerByUserIdOrNull(Long userId) {
        return timerRepository.findTimersByUserEntityIdAndStatusNot(userId, TimerStatus.COMPLETE)
                .orElse(null);
    }
    public List<Timer> getOrCreateTimerByUserId(long userId) {
        return setMessageIdToAnyCompleteTimerByUserId(userId, 0);
    }
    public List<Timer> setMessageIdToAnyCompleteTimerByUserId(Long userId, int messageId) {
        Optional<List<Timer>> timers = timerRepository
                .findTimersByUserEntityIdAndStatusNot(userId, TimerStatus.COMPLETE);
        if (timers.isPresent() && !timers.get().isEmpty()) {
            if (messageId != 0) timers.get().get(0).setTelegramMessageId(messageId);
            return timerRepository.saveAll(timers.get());
        } else {
            UserEntity user = pomidoroUserService.getUserByEntityIdOrNull(userId);
            List<Timer> timerList = timers.get();
            timerList.add(new Timer().setUserEntity(user).setStatus(TimerStatus.PENDING));
            return timerRepository.saveAll(timerList);
        }
    }

    public TimerDto getTimerDtoById(Long timerId) {
        return timerMapper.entityToDto(getTimerByIdOrNull(timerId));
    }

    private Timer getTimerByIdOrNull(Long timerId) {
        return timerRepository.findById(timerId)
                .orElse(null);
    }

    public TimerDto createTimerOrNull(TimerDto timerDto) {
        if (timerDto.getUser() == null || timerDto.getUser().getId() == null) {
            log.error("TIMER_CREATION_ERROR");
            return null;
        }
        return timerMapper.entityToDto(
                getOrCreateTimerByUserId(timerDto.getUser().getId()).get(0)
        );
    }

    public TimerDto updateTimerDto(TimerDto timerDto) {
        return timerMapper.entityToDto(updateTimerOrNull(timerMapper.dtoToEntity(timerDto)));
    }

    public Timer updateTimerOrNull(Timer timer) {
        if (timer.getId() == null || TimerStatus.COMPLETE.equals(timer.getStatus())) {
            log.error("TIMER_UPDATE_ERROR");
            return null;
        }
        return timerRepository.save(timer);
    }

    public TimerDto updateTimerDtoStatus(Long timerId, String status) {
        return timerMapper.entityToDto(
                updateTimerStatusOrNull(timerId, status));
    }

    /**
     * Находит таймеры с истекшим временем
     * В зависимости от настроек, имеет логику
     * автоматического изменения статуса таймера
     * @return null - if no timer has expired
     */
    @Transactional
    public List<Timer> getExpiredTimersAndUpdate() {
        Optional<List<Timer>> expiredTimers = timerRepository.findAllExpiredAndNotComplete(LocalDateTime.now());
        if (!expiredTimers.isPresent()) return null;

        List<Timer> timers = expiredTimers.get();
        for (Timer timer: timers) {
            timer.setInterval(timer.getInterval() + 1);
            timer.setMinuteToStop(0);
            updateTimerOrNull(timer);
            //Автоматический запуск таймера, если установлены необходимые настройки
            if ((timer.isAutostartBreak() && timer.getInterval() % 2 == 0) ||
                    (timer.isAutostartWork() && timer.getInterval() % 2 != 0)) {
                updateTimerStatusOrNull(timer.getId(), TimerStatus.RUNNING.name());
            } else {
                updateTimerStatusOrNull(timer.getId(), PAUSED.name());
            }
        }
        return expiredTimers.get();
    }

    public void deleteTimerById(Long timerId) {
        timerRepository.deleteById(timerId);
    }

    @Transactional
    public TimerDto bindTaskToTimerOrNull(Long timerId, Long taskId) {
        Timer timer = getTimerByIdOrNull(timerId);
        Task task = taskService.getTaskByIdOrNull(taskId);
        if (!timer.getUserEntity().getId()
                .equals(task.getUserEntity().getId())) {
            log.error("TIMER_TASK_ERROR");
            return null;
        }
        timer.getTasks().add(task);
        return timerMapper.entityToDto(
                timerRepository.save(timer)
        );
    }

    public TimerDto unbindTaskFromTimerOrNull(Long timerId, Long taskId) {
        Timer timer = getTimerByIdOrNull(timerId);
        Set<Task> taskList = timer.getTasks();

        Optional<Task> taskOptional = taskList.stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst();

        if (taskOptional.isPresent()) {
            taskList.remove(taskOptional.get());
        } else {
            log.error("TIMER_TASK_ERROR");
            return null;
        }
        return timerMapper.entityToDto(timerRepository.save(timer));
    }

    public TimerDto unbindAllTasksFromTimer(Long timerId) {
        Timer timer = getTimerByIdOrNull(timerId);
        timer.getTasks().clear();
        return timerMapper.entityToDto(timerRepository.save(timer));
    }

    /**
     * Обновление статуса таймера.
     * PENDING:
     *      Приводит таймер к нерабочему состоянию.
     *      Обнуляет время остановки. При запуске,
     *      в зависимости от интервала, время до
     *      остановки будет вычислено исходя из
     *      времени интервала
     * RUNNING:
     *      Если время ДО остановки не обнуленно,
     *      вычисляет точное время остановки, исходя
     *      из этого значения.
     *      Если время до остановки не имеет значения,
     *      вычисляет время остановки исходя из текущего
     *      интервала.
     *      Чётный интервал - время работы
     *      Нечётный интервал - пауза
     * PAUSED:
     *      Вычисляет оставшееся время до остановки и
     *      сохраняет.
     * @param timerId
     * @param status
     * @return
     */
    @Transactional
    public Timer updateTimerStatusOrNull(Long timerId, String status) {
        Timer timer = getTimerByIdOrNull(timerId);
        if (timer.getStatus().equals(TimerStatus.COMPLETE)) {
            log.error("TIMER_ERROR");
            return null;
        }
        timer.setStatus(TimerStatus.valueOf(status));
        LocalDateTime stopTime = LocalDateTime.now();
        switch (TimerStatus.valueOf(status)) {
            case PENDING:
                timer.setMinuteToStop(timer.getWorkDuration());
                timer.setStopTime(null);
                break;
            case RUNNING:
                if (timer.getMinuteToStop() != 0) {
                    stopTime = stopTime.plusMinutes(timer.getMinuteToStop());
                } else if (timer.getLongBreakInterval() != 0 && timer.getInterval() % 2 != 0
                        && timer.getInterval() / 2 % timer.getLongBreakInterval() != 0) {
                    stopTime = stopTime.plusMinutes(timer.getLongBreakDuration());
                } else if (timer.getLongBreakInterval() != 0 && timer.getInterval() % 2 != 0 ) {
                    stopTime = stopTime.plusMinutes(timer.getShortBreakDuration());
                } else {
                    stopTime = stopTime.plusMinutes(timer.getWorkDuration());
                }
                timer.setStopTime(stopTime);
                break;
            case PAUSED:
                if (timer.getStopTime() == null) {
                    return updateTimerStatusOrNull(timerId, String.valueOf(PENDING));
                }
                int timeToStop = (int) Duration.between(LocalDateTime.now(), timer.getStopTime()).getSeconds() / 60;
                timer.setMinuteToStop(timeToStop);
                timer.setStopTime(null);
                break;
        }
        return timerRepository.save(timer);
    }

    public List<Timer> getCompletedTimersByUserIdAndCreatedAfterDate(long userId, LocalDateTime localDate) {
        return timerRepository.findTimersByUserEntityIdAndCreatedAfter(userId, localDate);
    }

}
