package com.socialmedia.auth.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.socialmedia.auth.dto.GoogleUserProfile;
import com.socialmedia.auth.dto.IssuedAuthTokens;
import com.socialmedia.auth.entities.Role;
import com.socialmedia.auth.entities.User;
import com.socialmedia.auth.repositories.RoleRepository;
import com.socialmedia.auth.repositories.UserRepository;
import com.socialmedia.auth.repositories.UserTokenRepository;
import com.socialmedia.auth.security.JwtService;

@ExtendWith(MockitoExtension.class)
class UserServiceGoogleLoginTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private GoogleIdTokenVerifierService googleIdTokenVerifierService;
    @Mock
    private UserTokenRepository userTokenRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private KafkaEventProducer kafkaEventProducer;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                roleRepository,
                passwordEncoder,
                jwtService,
                googleIdTokenVerifierService,
                userTokenRepository,
                emailService,
                kafkaEventProducer);
    }

    @Test
    void loginWithGoogle_shouldIssueTokensForExistingUser() {
        GoogleUserProfile googleUserProfile = new GoogleUserProfile("google-sub", "john@example.com", "John Doe");
        Role role = new Role();
        role.setRoleName("USER");
        User existingUser = new User();
        existingUser.setId("user-1");
        existingUser.setUsername("johndoe");
        existingUser.setEmail("john@example.com");
        existingUser.setFullName("John Doe");
        existingUser.setRole(role);
        existingUser.setIsActive(true);
        existingUser.setLockedUntil(Instant.now().plusSeconds(60));
        existingUser.setLoginFailedCount(3);

        when(googleIdTokenVerifierService.verify("google-id-token")).thenReturn(googleUserProfile);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(existingUser)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(existingUser)).thenReturn("refresh-token");
        when(jwtService.extractExpiration("access-token")).thenReturn(java.util.Date.from(Instant.now().plusSeconds(600)));
        when(jwtService.extractExpiration("refresh-token")).thenReturn(java.util.Date.from(Instant.now().plusSeconds(86400)));

        IssuedAuthTokens tokens = userService.loginWithGoogle("google-id-token");

        assertNotNull(tokens);
        assertEquals("access-token", tokens.getAuthResponse().getAccessToken());
        assertEquals("john@example.com", tokens.getAuthResponse().getEmail());
        assertEquals(0, existingUser.getLoginFailedCount());
        assertEquals(null, existingUser.getLockedUntil());
        verify(userTokenRepository).deleteByUserId("user-1");
        verify(roleRepository, never()).findByRoleName(any());
        verify(kafkaEventProducer, never()).publishCreateProfileEvent(any());
    }

    @Test
    void loginWithGoogle_shouldCreateNewUserWhenEmailDoesNotExist() {
        GoogleUserProfile googleUserProfile = new GoogleUserProfile("google-sub", "new.user@example.com", "New User");
        Role role = new Role();
        role.setRoleName("USER");

        when(googleIdTokenVerifierService.verify("google-id-token")).thenReturn(googleUserProfile);
        when(userRepository.findByEmail("new.user@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("new.user")).thenReturn(false);
        when(roleRepository.findByRoleName("USER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(any(String.class))).thenReturn("encoded-random-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId("new-user-id");
            }
            return user;
        });
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh-token");
        when(jwtService.extractExpiration("access-token")).thenReturn(java.util.Date.from(Instant.now().plusSeconds(600)));
        when(jwtService.extractExpiration("refresh-token")).thenReturn(java.util.Date.from(Instant.now().plusSeconds(86400)));

        IssuedAuthTokens tokens = userService.loginWithGoogle("google-id-token");

        assertNotNull(tokens);
        assertEquals("new-user-id", tokens.getAuthResponse().getId());
        assertEquals("new.user@example.com", tokens.getAuthResponse().getEmail());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(userCaptor.capture());
        assertTrue(userCaptor.getAllValues().stream().anyMatch(savedUser -> "new.user@example.com".equals(savedUser.getEmail())));
        verify(kafkaEventProducer).publishCreateProfileEvent(any());
        verify(userTokenRepository).deleteByUserId("new-user-id");
    }
}