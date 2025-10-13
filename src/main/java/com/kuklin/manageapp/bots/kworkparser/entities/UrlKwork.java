package com.kuklin.manageapp.bots.kworkparser.entities;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "url_kwork")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UrlKwork {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long kworkId;
    private Long urlId;
}
