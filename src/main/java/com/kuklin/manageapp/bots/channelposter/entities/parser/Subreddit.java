package com.kuklin.manageapp.bots.channelposter.entities.parser;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "subreddit")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Subreddit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String name;

    @Column
    private String url;

    @Column
    private Boolean active;

    @Column(name = "created")
    @CreationTimestamp
    private Instant created;

}
