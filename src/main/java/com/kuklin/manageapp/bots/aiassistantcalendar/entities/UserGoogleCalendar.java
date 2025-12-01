package com.kuklin.manageapp.bots.aiassistantcalendar.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "user_google_calendar")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserGoogleCalendar {
    @Id
    private Long telegramId;
    private String calendarId;

}
