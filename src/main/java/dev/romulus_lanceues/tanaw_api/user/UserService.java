package dev.romulus_lanceues.tanaw_api.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createUser(String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email is already registered: " + email);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        log.info("User created: userId = {}", savedUser.getId());

        return UserResponse.from(savedUser);
    }


    public Optional<UserResponse> findById(UUID id) {
        return userRepository.findById(id).map(UserResponse::from);
    }


    public Optional<UserResponse> findActiveByEmail(String email) {
        return userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE).map(UserResponse::from);
    }


    @Transactional
    public void changePassword(User user, String currentRawPassword, String newRawPassword) {
        if (!verifyPassword(user, currentRawPassword)) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.updatePassword(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);

        log.info("Password changed: userId={}", user.getId());
    }

    public boolean verifyPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    @Transactional
    public void disableUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow( () -> new UserNotFoundException("User not found: " + id ));

        user.updateStatus(UserStatus.DISABLED);
        userRepository.save(user);

        log.info("User disabled: userId={}", user.getId());
    }


}
