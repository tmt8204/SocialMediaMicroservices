package com.socialmedia.auth.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.socialmedia.auth.entities.Role;
import com.socialmedia.auth.repositories.RoleRepository;

@Component
public class RoleDataInitializer implements CommandLineRunner {

    private static final List<String> DEFAULT_ROLES = List.of("USER", "ADMIN");

    private final RoleRepository roleRepository;

    public RoleDataInitializer(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) {
        for (String roleName : DEFAULT_ROLES) {
            roleRepository.findByRoleName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
        }
    }
}
