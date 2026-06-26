package com.kuklin.manageapp.common.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "app_users")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String passwordHash;

    // Общие данные
    private String username;
    private String firstname;
    private String lastname;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updated;
    @CreationTimestamp
    @Column(name = "created_at")
    private Instant created;
}
