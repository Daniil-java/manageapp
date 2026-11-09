package com.kuklin.manageapp.bots.aiassistantcalendar.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CalendarEventAiResponse {
    String summary;
    String description;
    String start;
    String end;
    String timezone;
    String result;
    List<Integer> notifyInMinutesList;
    Boolean isSuccessful;

}
