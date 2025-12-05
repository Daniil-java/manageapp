package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "ai_message_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class AiMessageLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String request;
    private String response;

}
