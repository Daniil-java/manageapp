package com.kuklin.manageapp.bots.pomidorotimer.entities;

import com.kuklin.manageapp.bots.pomidorotimer.models.timer.TimerStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "timers")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class Timer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity userEntity;

    @Enumerated(EnumType.STRING)
    private TimerStatus status;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.EAGER)
    @JoinTable(
            name = "timer_tasks",
            joinColumns = @JoinColumn(name = "timer_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "task_id", referencedColumnName = "id")
    )
    private Set<Task> tasks;

    private int workDuration;

    private int shortBreakDuration;

    private int longBreakDuration;

    private int longBreakInterval;

    private boolean isAutostartWork;

    private boolean isAutostartBreak;

    private int interval;

    private int telegramMessageId;

    private LocalDateTime stopTime;

    private int minuteToStop;

    @UpdateTimestamp
    private LocalDateTime updated;

    @CreationTimestamp
    private LocalDateTime created;


}
