package dev.romulus_lanceues.tanaw_api.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("should create and return user when email is not taken")
        void shouldCreateAndReturnUser_whenEmailIsNotTaken() {

            String email = "alice@example.com";
            String rawPassword = "SecurePass123!";
            String encodedPassword = "encoded_hash_abc";

            User savedUser = User.builder()
                    .id(UUID.randomUUID())
                    .email(email)
                    .passwordHash(encodedPassword)
                    .status(UserStatus.ACTIVE)
                    .build();

            given(userRepository.existsByEmail(email)).willReturn(false);
            given(passwordEncoder.encode(rawPassword)).willReturn(encodedPassword);
            given(userRepository.save(any(User.class))).willReturn(savedUser);


            User result = userService.createUser(email, rawPassword);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(email);
            assertThat(result.getPasswordHash()).isEqualTo(encodedPassword);
            assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("should throw UserAlreadyExistsException when email is already registered")
        void shouldThrowUserAlreadyExistsException_whenEmailIsAlreadyRegistered() {

            String email = "alice@example.com";

            given(userRepository.existsByEmail(email)).willReturn(true);


            assertThatThrownBy(() -> userService.createUser(email, "AnyPassword1!"))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining(email);

            then(userRepository).should(never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return user when user exists")
        void shouldReturnUser_whenUserExists() {

            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("alice@example.com")
                    .passwordHash("hash_123")
                    .status(UserStatus.ACTIVE)
                    .build();

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            Optional<User> result = userService.findById(userId);

            assertThat(result)
                    .isPresent()
                    .hasValueSatisfying(found -> {
                        assertThat(found.getId()).isEqualTo(userId);
                        assertThat(found.getEmail()).isEqualTo("alice@example.com");
                    });
        }

        @Test
        @DisplayName("should return empty when user does not exist")
        void shouldReturnEmpty_whenUserDoesNotExist() {

            UUID userId = UUID.randomUUID();

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            Optional<User> result = userService.findById(userId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findActiveByEmail")
    class FindActiveByEmail {

        @Test
        @DisplayName("should return user when active user with email exists")
        void shouldReturnUser_whenActiveUserWithEmailExists() {

            String email = "alice@example.com";
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .email(email)
                    .passwordHash("hash_123")
                    .status(UserStatus.ACTIVE)
                    .build();

            given(userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE))
                    .willReturn(Optional.of(user));

            Optional<User> result = userService.findActiveByEmail(email);

            assertThat(result)
                    .isPresent()
                    .hasValueSatisfying(found -> {
                        assertThat(found.getEmail()).isEqualTo(email);
                        assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
                    });
        }

        @Test
        @DisplayName("should return empty when no active user with email exists")
        void shouldReturnEmpty_whenNoActiveUserWithEmailExists() {

            String email = "disabled@example.com";

            given(userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE))
                    .willReturn(Optional.empty());


            Optional<User> result = userService.findActiveByEmail(email);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("verifyPassword")
    class VerifyPassword {

        @Test
        @DisplayName("should return true when raw password matches stored hash")
        void shouldReturnTrue_whenRawPasswordMatchesStoredHash() {

            User user = User.builder()
                    .id(UUID.randomUUID())
                    .email("alice@example.com")
                    .passwordHash("encoded_hash_abc")
                    .status(UserStatus.ACTIVE)
                    .build();

            given(passwordEncoder.matches("CorrectPassword1!", "encoded_hash_abc"))
                    .willReturn(true);

            boolean result = userService.verifyPassword(user, "CorrectPassword1!");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when raw password does not match stored hash")
        void shouldReturnFalse_whenRawPasswordDoesNotMatchStoredHash() {

            User user = User.builder()
                    .id(UUID.randomUUID())
                    .email("alice@example.com")
                    .passwordHash("encoded_hash_abc")
                    .status(UserStatus.ACTIVE)
                    .build();

            given(passwordEncoder.matches("WrongPassword!", "encoded_hash_abc"))
                    .willReturn(false);

            boolean result = userService.verifyPassword(user, "WrongPassword!");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("should update password when current password is correct")
        void shouldUpdatePassword_whenCurrentPasswordIsCorrect() {

            User user = User.builder()
                    .id(UUID.randomUUID())
                    .email("alice@example.com")
                    .passwordHash("old_encoded_hash")
                    .status(UserStatus.ACTIVE)
                    .build();

            String currentRawPassword = "OldPassword1!";
            String newRawPassword = "NewPassword2!";
            String newEncodedPassword = "new_encoded_hash";

            given(passwordEncoder.matches(currentRawPassword, "old_encoded_hash"))
                    .willReturn(true);
            given(passwordEncoder.encode(newRawPassword)).willReturn(newEncodedPassword);

            userService.changePassword(user, currentRawPassword, newRawPassword);

            assertThat(user.getPasswordHash()).isEqualTo(newEncodedPassword);
            then(userRepository).should().save(user);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when current password is incorrect")
        void shouldThrowIllegalArgumentException_whenCurrentPasswordIsIncorrect() {

            User user = User.builder()
                    .id(UUID.randomUUID())
                    .email("alice@example.com")
                    .passwordHash("encoded_hash_abc")
                    .status(UserStatus.ACTIVE)
                    .build();

            given(passwordEncoder.matches("WrongPassword!", "encoded_hash_abc"))
                    .willReturn(false);

            assertThatThrownBy(() -> userService.changePassword(user, "WrongPassword!", "NewPass1!"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Current password is incorrect");

            then(passwordEncoder).should(never()).encode(anyString());
            then(userRepository).should(never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("disableUser")
    class DisableUser {

        @Test
        @DisplayName("should disable user when user exists")
        void shouldDisableUser_whenUserExists() {

            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("alice@example.com")
                    .passwordHash("hash_123")
                    .status(UserStatus.ACTIVE)
                    .build();

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            userService.disableUser(userId);

            assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
            then(userRepository).should().save(user);
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user does not exist")
        void shouldThrowUserNotFoundException_whenUserDoesNotExist() {

            UUID userId = UUID.randomUUID();

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.disableUser(userId))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(userId.toString());

            then(userRepository).should(never()).save(any(User.class));
        }
    }
}
