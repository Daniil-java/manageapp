package com.kuklin.manageapp.common.services;

import com.kuklin.manageapp.common.entities.Role;
import com.kuklin.manageapp.common.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public Optional<Role> findByRoleName(Role.RoleName name) {
        return roleRepository.findByRoleName(name);
    }
}
