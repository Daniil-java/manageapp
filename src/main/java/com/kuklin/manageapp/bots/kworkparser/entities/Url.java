package com.kuklin.manageapp.bots.kworkparser.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "urls")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String url;
    @UpdateTimestamp
    private LocalDateTime updated;
    @CreationTimestamp
    private LocalDateTime created;
}
