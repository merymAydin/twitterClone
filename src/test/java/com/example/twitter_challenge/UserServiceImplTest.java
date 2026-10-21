package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.SubscriptionRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.UserServiceImpl;
import com.example.twitter_challenge.dto.request.CreateUserRequest;
import com.example.twitter_challenge.dto.response.PublicUserResponse;
import com.example.twitter_challenge.exception.ForbiddenException;
import com.example.twitter_challenge.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registrationNormalizesEmailAndRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

        CreateUserRequest request = new CreateUserRequest(
                "alice",
                "  ALICE@Example.com ",
                "password123",
                null,
                null,
                null,
                null,
                null
        );

        assertThrows(UserAlreadyExistsException.class, () -> userService.save(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void publicProfileDoesNotContainPrivateFields() {
        User user = activeUser(1L, "alice@example.com");
        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));

        PublicUserResponse response = userService.findPublicById(1L);

        assertEquals(1L, response.userId());
        assertEquals("alice", response.username());
    }

    @Test
    void premiumBadgeIsDerivedFromAnEntitledSubscription() {
        User user = activeUser(1L, "alice@example.com");
        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.existsByUserIdAndSubscriptionStatusInAndIsDeletedFalse(
                any(Long.class),
                any()
        )).thenReturn(true);

        PublicUserResponse response = userService.findPublicById(1L);

        org.junit.jupiter.api.Assertions.assertTrue(response.premium());
    }

    @Test
    void userCannotUpdateAnotherUsersProfile() {
        User victim = activeUser(2L, "victim@example.com");
        User attacker = activeUser(3L, "attacker@example.com");
        when(userRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(victim));
        when(userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc("attacker@example.com"))
                .thenReturn(Optional.of(attacker));
        authenticateAs("attacker@example.com");

        assertThrows(
                ForbiddenException.class,
                () -> userService.update(2L, new com.example.twitter_challenge.dto.request.UpdateUserRequest(
                        "attacker",
                        "attacker@example.com",
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void userCannotDeleteAnotherUsersProfile() {
        User victim = activeUser(2L, "victim@example.com");
        User attacker = activeUser(3L, "attacker@example.com");
        when(userRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(victim));
        when(userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc("attacker@example.com"))
                .thenReturn(Optional.of(attacker));
        authenticateAs("attacker@example.com");

        assertThrows(ForbiddenException.class, () -> userService.removeUser(2L));
        verify(userRepository, never()).save(victim);
    }

    @Test
    void ownerCanSoftDeleteOwnProfile() {
        User user = activeUser(1L, "alice@example.com");
        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc("alice@example.com"))
                .thenReturn(Optional.of(user));
        authenticateAs("alice@example.com");

        userService.removeUser(1L);

        org.junit.jupiter.api.Assertions.assertTrue(user.getIsDeleted());
        verify(userRepository).save(user);
    }

    private User activeUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setUserName("alice");
        user.setPassword("hash");
        user.setIsDeleted(false);
        return user;
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null)
        );
    }
}
