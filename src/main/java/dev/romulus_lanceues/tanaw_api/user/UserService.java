package dev.romulus_lanceues.tanaw_api.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("Email is already registered: " + request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        log.info("User created: userId = {}", savedUser.getId());

        return UserResponse.from(savedUser);
    }

    public UserResponse findById(UUID id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    }

    public UserResponse findActiveByEmail(String email) {
        return userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException("Active user not found with email: " + email));
    }

    @Transactional
    public void changePassword(UUID id, ChangePasswordRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidPasswordException("User account is not active");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException("New password cannot be the same as the current password");
        }

        user.updatePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        log.info("Password changed: userId={}", user.getId());
    }

    @Transactional
    public void disableUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));

        user.updateStatus(UserStatus.DISABLED);
        userRepository.save(user);

        log.info("User disabled: userId={}", user.getId());
    }
}
