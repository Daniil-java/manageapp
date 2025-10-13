package com.kuklin.manageapp.bots.aiassistantcalendar.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.api.services.calendar.model.EventDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CalendarEventAddRequest {
    Long calendarId;
    String summary;
    String description;
    EventDateTime start;
    EventDateTime end;
}
