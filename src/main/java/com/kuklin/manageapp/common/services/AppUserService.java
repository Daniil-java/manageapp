package com.kuklin.manageapp.common.services;

import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.common.entities.AppUser;
import com.kuklin.manageapp.common.entities.Role;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppUserService {
    private final AppUserRepository repository;
    private final RoleService roleService;

    public AppUser getAppUserByIdOrNull(Long userId) {
        return repository.findById(userId).orElse(null);
    }

    public AppUser createUser(AppUser user) {
        if (user.getRoles().isEmpty()) {
            roleService.findByRoleName(Role.RoleName.ROLE_USER).ifPresent(user::addRole);
        }
        return repository.save(user);
    }

    public AppUser createUserByTelegram(User tgUser) {
        return createUser(
                new AppUser()
                        .setUsername(tgUser.getUserName())
                        .setFirstname(tgUser.getFirstName())
                        .setLastname(tgUser.getLastName())
        );
    }

    public AppUser createUserByTelegram(TelegramUser tgUser) {
        return createUser(
                new AppUser()
                        .setUsername(tgUser.getUsername())
                        .setFirstname(tgUser.getFirstname())
                        .setLastname(tgUser.getLastname())
        );
    }

    public Optional<AppUser> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Transactional
    public AppUser addRole(Long appUserId, Role role) {
        AppUser user = repository.findById(appUserId)
                .orElseThrow(() -> new ErrorResponseException(ErrorStatus.USER_NOT_FOUND));
        user.addRole(role);
        return repository.save(user);
    }

    @Transactional
    public AppUser removeRole(Long appUserId, Role.RoleName roleName) {
        AppUser user = repository.findById(appUserId)
                .orElseThrow(() -> new ErrorResponseException(ErrorStatus.USER_NOT_FOUND));
        user.getRoles().removeIf(r -> r.getRoleName() == roleName);
        return repository.save(user);
    }
}
