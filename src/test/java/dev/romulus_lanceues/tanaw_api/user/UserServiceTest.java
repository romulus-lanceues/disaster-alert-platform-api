package dev.romulus_lanceues.tanaw_api.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        @DisplayName("should create and return user response when email is not taken")
        void shouldCreateAndReturnUser_whenEmailIsNotTaken() {

            CreateUserRequest request = new CreateUserRequest("alice@example.com", "SecurePass123!");
            String encodedPassword = "encoded_hash_abc";
            UUID userId = UUID.randomUUID();

            User savedUser = User.builder()
                    .id(userId)
                    .email(request.email())
                    .passwordHash(encodedPassword)
                    .status(UserStatus.ACTIVE)
                    .build();

            given(userRepository.existsByEmail(request.email())).willReturn(false);
            given(passwordEncoder.encode(request.password())).willReturn(encodedPassword);
            given(userRepository.save(any(User.class))).willReturn(savedUser);

            UserResponse result = userService.createUser(request);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(userId);
            assertThat(result.email()).isEqualTo(request.email());
            assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            then(userRepository).should().save(captor.capture());

            User createdUser = captor.getValue();
            assertThat(createdUser.getEmail()).isEqualTo(savedUser.getEmail());
            assertThat(createdUser.getPasswordHash()).isEqualTo(savedUser.getPasswordHash());
            assertThat(createdUser.getStatus()).isEqualTo(savedUser.getStatus());
            assertThat(createdUser.getAuthenticationVersion()).isEqualTo(0L);
        }

        @Test
        @DisplayName("should throw UserAlreadyExistsException when email is already registered")
        void shouldThrowUserAlreadyExistsException_whenEmailIsAlreadyRegistered() {

            CreateUserRequest request = new CreateUserRequest("alice@example.com", "AnyPassword1!");

            given(userRepository.existsByEmail(request.email())).willReturn(true);

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining(request.email());

            then(userRepository).should(never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return user response when user exists")
        void shouldReturnUser_whenUserExists() {

            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("alice@example.com")
                    .passwordHash("hash_123")
                    .status(UserStatus.ACTIVE)
                    .build();

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            UserResponse result = userService.findById(userId);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(userId);
            assertThat(result.email()).isEqualTo("alice@example.com");
            assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user does not exist")
        void shouldThrowUserNotFoundException_whenUserDoesNotExist() {

            UUID userId = UUID.randomUUID();

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findById(userId))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(userId.toString());
        }
    }

    @Nested
    @DisplayName("findActiveByEmail")
    class FindActiveByEmail {

        @Test
        @DisplayName("should return user response when active user with email exists")
        void shouldReturnUser_whenActiveUserWithEmailExists() {

            String email = "alice@example.com";
            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email(email)
                    .passwordHash("hash_123")
                    .status(UserStatus.ACTIVE)
                    .build();

            given(userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE))
                    .willReturn(Optional.of(user));

            UserResponse result = userService.findActiveByEmail(email);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(userId);
            assertThat(result.email()).isEqualTo(email);
            assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("should throw UserNotFoundException when no active user with email exists")
        void shouldThrowUserNotFoundException_whenNoActiveUserWithEmailExists() {

            String email = "disabled@example.com";

            given(userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findActiveByEmail(email))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(email);
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("should update password when user exists, is active, and current password is correct")
        void shouldUpdatePassword_whenCurrentPasswordIsCorrect() {

            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("alice@example.com")
                    .passwordHash("old_encoded_hash")
                    .status(UserStatus.ACTIVE)
                    .build();

            ChangePasswordRequest request = new ChangePasswordRequest("OldPassword1!", "NewPassword2!");
            String newEncodedPassword = "new_encoded_hash";

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(request.currentPassword(), "old_encoded_hash")).willReturn(true);
            given(passwordEncoder.matches(request.newPassword(), "old_encoded_hash")).willReturn(false);
            given(passwordEncoder.encode(request.newPassword())).willReturn(newEncodedPassword);

            userService.changePassword(userId, request);

            assertThat(user.getPasswordHash()).isEqualTo(newEncodedPassword);
            then(userRepository).should().save(user);
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user does not exist")
        void shouldThrowUserNotFoundException_whenUserDoesNotExist() {

            UUID userId = UUID.randomUUID();
            ChangePasswordRequest request = new ChangePasswordRequest("OldPassword1!", "NewPassword2!");

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(userId.toString());

            then(passwordEncoder).should(never()).matches(anyString(), anyString());
            then(userRepository).should(never()).save(any(User.class));
        }

        @Test
        @DisplayName("should throw InvalidPasswordException when user is not active")
        void shouldThrowInvalidPasswordException_whenUserIsNotActive() {

            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("alice@example.com")
                    .passwordHash("old_encoded_hash")
                    .status(UserStatus.DISABLED)
                    .build();

            ChangePasswordRequest request = new ChangePasswordRequest("OldPassword1!", "NewPassword2!");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessageContaining("User account is not active");

            then(passwordEncoder).should(never()).matches(anyString(), anyString());
            then(userRepository).should(never()).save(any(User.class));
        }

        @Test
        @DisplayName("should throw InvalidPasswordException when current password is incorrect")
        void shouldThrowInvalidPasswordException_whenCurrentPasswordIsIncorrect() {

            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("alice@example.com")
                    .passwordHash("encoded_hash_abc")
                    .status(UserStatus.ACTIVE)
                    .build();

            ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword!", "NewPass1!");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(request.currentPassword(), "encoded_hash_abc")).willReturn(false);

            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessageContaining("Current password is incorrect");

            then(passwordEncoder).should(never()).encode(anyString());
            then(userRepository).should(never()).save(any(User.class));
        }

        @Test
        @DisplayName("should throw InvalidPasswordException when new password is the same as current password")
        void shouldThrowInvalidPasswordException_whenNewPasswordIsSameAsCurrentPassword() {

            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("alice@example.com")
                    .passwordHash("encoded_hash_abc")
                    .status(UserStatus.ACTIVE)
                    .build();

            ChangePasswordRequest request = new ChangePasswordRequest("SamePassword1!", "SamePassword1!");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(request.currentPassword(), "encoded_hash_abc")).willReturn(true);
            given(passwordEncoder.matches(request.newPassword(), "encoded_hash_abc")).willReturn(true);

            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessageContaining("New password cannot be the same as the current password");

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
