package com.kuklin.manageapp.bots.nicotinebot.components;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "smoking_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class SmokingRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "smoked_at", nullable = false)
    @CreationTimestamp
    private Instant smokedAt;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static String format(Instant instant, ZoneId zoneId) {
        if (instant == null || zoneId == null) {
            return null;
        }

        return FORMATTER
                .withZone(zoneId)
                .format(instant);
    }

    public static String diffHHmm(Instant from, Instant to) {
        if (from == null || to == null) {
            return null;
        }

        Duration duration = Duration.between(from, to).abs();

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart(); // Java 9+

        return String.format("%02d:%02d", hours, minutes);
    }
}
