package com.socialmedia.auth.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.socialmedia.auth.entities.Role;
import com.socialmedia.auth.entities.User;
import com.socialmedia.auth.repositories.RoleRepository;
import com.socialmedia.auth.repositories.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RoleDataInitializer implements CommandLineRunner {

    private static final List<String> DEFAULT_ROLES = List.of("USER", "MODERATOR", "ADMIN");

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.username:superadmin}")
    private String bootstrapAdminUsername;

    @Value("${app.bootstrap.admin.email:superadmin@gummynetwork.local}")
    private String bootstrapAdminEmail;

    @Value("${app.bootstrap.admin.password:Admin@123}")
    private String bootstrapAdminPassword;

    @Value("${app.bootstrap.admin.full-name:Super Admin}")
    private String bootstrapAdminFullName;

    public RoleDataInitializer(RoleRepository roleRepository,
                               UserRepository userRepository,
                               PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        for (String roleName : DEFAULT_ROLES) {
            roleRepository.findByRoleName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
        }

        Role adminRole = roleRepository.findByRoleName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found after bootstrap"));

        upsertBootstrapAdminUser(adminRole);
    }

    private void upsertBootstrapAdminUser(Role adminRole) {
        userRepository.findByUsername(bootstrapAdminUsername).ifPresentOrElse(user -> {
            boolean changed = false;
            boolean isAlreadyAdmin = user.getRole() != null && "ADMIN".equals(user.getRole().getRoleName());

            if (!isAlreadyAdmin) {
                user.setRole(adminRole);
                changed = true;
            }

            if (changed) {
                userRepository.save(user);
                log.info("Assigned ADMIN role to existing bootstrap user '{}'", bootstrapAdminUsername);
            }
        }, () -> {
            if (userRepository.existsByEmail(bootstrapAdminEmail)) {
                log.warn("Skip bootstrap admin creation: email '{}' already exists with another account", bootstrapAdminEmail);
                return;
            }

            User adminUser = new User();
            adminUser.setUsername(bootstrapAdminUsername);
            adminUser.setEmail(bootstrapAdminEmail);
            adminUser.setPasswordHash(passwordEncoder.encode(bootstrapAdminPassword));
            adminUser.setRole(adminRole);
            adminUser.setFullName(bootstrapAdminFullName);
            adminUser.setEmailVerified(true);
            adminUser.setIsActive(true);

            userRepository.save(adminUser);
            log.info("Created bootstrap admin account with username '{}'", bootstrapAdminUsername);
        });
    }
}
