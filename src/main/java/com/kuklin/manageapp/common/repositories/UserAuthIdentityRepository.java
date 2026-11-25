package com.kuklin.manageapp.common.repositories;

import com.kuklin.manageapp.common.entities.UserAuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentity, Long> {

    Optional<UserAuthIdentity> findByProviderAndProviderId(UserAuthIdentity.AuthProvider provider, String providerId);

    boolean existsByAppUser_IdAndProvider(Long appUserId, UserAuthIdentity.AuthProvider provider);
}
