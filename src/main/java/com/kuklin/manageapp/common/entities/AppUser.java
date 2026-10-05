package com.kuklin.manageapp.common.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "roles")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(unique = true)
    private String email;
    private String passwordHash;

    private String username;
    private String firstname;
    private String lastname;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updated;
    @CreationTimestamp
    @Column(name = "created_at")
    private Instant created;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "app_user_roles",
            joinColumns = @JoinColumn(name = "app_user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public void addRole(Role role) {
        this.roles.add(role);
        role.getUsers().add(this);
    }
}
