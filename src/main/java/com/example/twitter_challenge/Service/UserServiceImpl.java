package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.SubscriptionRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.dto.request.CreateUserRequest;
import com.example.twitter_challenge.dto.request.UpdateUserRequest;
import com.example.twitter_challenge.dto.response.PublicUserResponse;
import com.example.twitter_challenge.dto.response.UserResponse;
import com.example.twitter_challenge.exception.ForbiddenException;
import com.example.twitter_challenge.exception.UserAlreadyExistsException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(
            UserRepository userRepository,
            SubscriptionRepository subscriptionRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return toResponse(requireActiveUser(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicUserResponse findPublicById(Long id) {
        return toPublicResponse(requireActiveUser(id));
    }

    @Override
    @Transactional
    public UserResponse save(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        String username = request.username().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new UserAlreadyExistsException("Email is already registered");
        }
        if (userRepository.existsByUserNameIgnoreCase(username)) {
            throw new UserAlreadyExistsException("Username is already taken");
        }

        User user = new User();
        user.setUserName(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setBirthday(request.birthday());
        user.setLocation(request.location());
        user.setBio(request.bio());
        user.setPhoto(request.photo());
        user.setBanner(request.banner());
        user.setIsDeleted(false);
        userRepository.save(user);
        return toResponse(user);
    }

    @Override
    @Transactional
    public void removeUser(Long id) {
        User user = requireActiveUser(id);
        requireOwner(user);
        user.setIsDeleted(true);
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        List<User> users = userRepository.findByIsDeletedFalse();
        Set<Long> premiumUserIds = premiumUserIds(users);
        return users.stream()
                .map(user -> toResponse(user, premiumUserIds.contains(user.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicUserResponse> findAllPublic() {
        List<User> users = userRepository.findByIsDeletedFalse();
        Set<Long> premiumUserIds = premiumUserIds(users);
        return users.stream()
                .map(user -> toPublicResponse(user, premiumUserIds.contains(user.getId())))
                .toList();
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = requireActiveUser(id);
        requireOwner(user);

        String username = request.username().trim();
        if (userRepository.existsByUserNameIgnoreCaseAndIdNot(username, id)) {
            throw new UserAlreadyExistsException("Username is already taken");
        }

        String email = request.email() == null || request.email().isBlank()
                ? user.getEmail()
                : normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new UserAlreadyExistsException("Email is already registered");
        }

        user.setUserName(username);
        user.setEmail(email);
        user.setBirthday(request.birthday());
        user.setLocation(request.location());
        user.setBio(request.bio());
        user.setPhoto(request.photo());
        user.setBanner(request.banner());
        userRepository.save(user);
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findByUserName(String username) {
        User user = userRepository.findByUserNameAndIsDeletedFalse(username)
                .orElseThrow(() -> new UserNotFoundException("User with " + username + " not found"));
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email) {
        User user = userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(email)
                .orElseThrow(() -> new UserNotFoundException("User with email " + email + " not found"));
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ForbiddenException("Authentication is required");
        }
        return findByEmail(authentication.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> findByUserNameContaining(String userName, Pageable pageable) {
        Page<User> users = userRepository
                .findByUserNameContainingIgnoreCaseAndIsDeletedFalse(userName, pageable);
        Set<Long> premiumUserIds = premiumUserIds(users.getContent());
        return users.map(user -> toResponse(user, premiumUserIds.contains(user.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PublicUserResponse> searchPublic(String userName, Pageable pageable) {
        Page<User> users = userRepository
                .findByUserNameContainingIgnoreCaseAndIsDeletedFalse(userName, pageable);
        Set<Long> premiumUserIds = premiumUserIds(users.getContent());
        return users.map(user -> toPublicResponse(user, premiumUserIds.contains(user.getId())));
    }

    private User requireActiveUser(Long id) {
        return userRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new UserNotFoundException("User with " + id + " not found"));
    }

    private void requireOwner(User user) {
        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository
                .findFirstByEmailAndIsDeletedFalseOrderByIdAsc(currentUserEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (!currentUser.getId().equals(user.getId())) {
            throw new ForbiddenException("You are not allowed to modify this user");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UserResponse toResponse(User user) {
        return toResponse(user, isPremium(user.getId()));
    }

    private UserResponse toResponse(User user, boolean premium) {
        return new UserResponse(
                user.getId(),
                user.getUserName(),
                user.getEmail(),
                user.getBirthday(),
                user.getLocation(),
                user.getBio(),
                user.getPhoto(),
                user.getBanner(),
                premium
        );
    }

    private PublicUserResponse toPublicResponse(User user) {
        return toPublicResponse(user, isPremium(user.getId()));
    }

    private PublicUserResponse toPublicResponse(User user, boolean premium) {
        return new PublicUserResponse(
                user.getId(),
                user.getUserName(),
                user.getLocation(),
                user.getBio(),
                user.getPhoto(),
                user.getBanner(),
                premium
        );
    }

    private boolean isPremium(Long userId) {
        return userId != null
                && subscriptionRepository.existsByUserIdAndSubscriptionStatusInAndIsDeletedFalse(
                        userId,
                        SubscriptionStatusPolicy.entitledStatuses()
                );
    }

    private Set<Long> premiumUserIds(Collection<User> users) {
        List<Long> userIds = users.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .toList();
        if (userIds.isEmpty()) {
            return Set.of();
        }
        List<Long> entitledUserIds = subscriptionRepository.findEntitledUserIds(
                userIds,
                SubscriptionStatusPolicy.entitledStatuses()
        );
        return entitledUserIds == null ? Set.of() : new HashSet<>(entitledUserIds);
    }
}
