package com.kuklin.manageapp.bots.channelposter.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Подписчики канала раз в день (снимок при утреннем отчёте) — для прироста за сутки и неделю.
 */
@Entity
@Table(name = "channel_daily_stats")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ChannelDailyStats {
    @Id
    private LocalDate day;
    private int subscribers;
    @CreationTimestamp
    private Instant created;
}
