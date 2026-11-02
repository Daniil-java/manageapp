package com.kuklin.manageapp.common.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;


@Entity
@Table(name = "user_auth_identities")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "appUser")
public class UserAuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;   // EMAIL, TELEGRAM, GOOGLE, APPLE и т.д.

    @Column(nullable = false, unique = true)
    private String providerId;       // email / telegram_id / google_sub и т.д.

    private String passwordHash;     // только для EMAIL

    private String email;            // может отличаться от основного

    private boolean verified = false;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public enum AuthProvider {
        EMAIL,
        TELEGRAM,
        GOOGLE,
        APPLE,
        // VK, YANDEX и т.д. в будущем
    }
}
