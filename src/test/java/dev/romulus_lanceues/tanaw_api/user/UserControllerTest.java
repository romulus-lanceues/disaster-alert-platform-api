package dev.romulus_lanceues.tanaw_api.user;

import dev.romulus_lanceues.tanaw_api.config.TestSecurityConfig;
import dev.romulus_lanceues.tanaw_api.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("UserController Tests")
class UserControllerTest {

    private static final String BASE_URL = "/api/v1/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @Nested
    @DisplayName("createUser (POST /api/v1/users)")
    class CreateUser {

        @Test
        @DisplayName("should return 201 Created and Location header when payload is valid")
        void shouldReturn201CreatedAndLocationHeader_whenPayloadIsValid() throws Exception {
            CreateUserRequest request = new CreateUserRequest("alice@example.com", "SecurePass123!");
            UUID userId = UUID.randomUUID();
            Instant now = Instant.parse("2026-09-18T10:00:00Z");
            UserResponse response = new UserResponse(userId, request.email(), UserStatus.ACTIVE, now, now);

            given(userService.createUser(any(CreateUserRequest.class))).willReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith(BASE_URL + "/" + userId)))
                    .andExpect(jsonPath("$.id", is(userId.toString())))
                    .andExpect(jsonPath("$.email", is(request.email())))
                    .andExpect(jsonPath("$.status", is("ACTIVE")))
                    .andExpect(jsonPath("$.createdAt", is(now.toString())))
                    .andExpect(jsonPath("$.updatedAt", is(now.toString())));

            then(userService).should().createUser(request);
        }

        @Test
        @DisplayName("should return 400 Bad Request when validation fails")
        void shouldReturn400BadRequest_whenValidationFails() throws Exception {
            CreateUserRequest invalidRequest = new CreateUserRequest("invalid-email", "short");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Failed")))
                    .andExpect(jsonPath("$.errors.email").exists())
                    .andExpect(jsonPath("$.errors.password").exists());

            then(userService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should return 409 Conflict when email is already registered")
        void shouldReturn409Conflict_whenEmailIsAlreadyRegistered() throws Exception {
            CreateUserRequest request = new CreateUserRequest("duplicate@example.com", "SecurePass123!");

            given(userService.createUser(any(CreateUserRequest.class)))
                    .willThrow(new UserAlreadyExistsException("Email is already registered: " + request.email()));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title", is("User Already Exists")))
                    .andExpect(jsonPath("$.detail", is("Email is already registered: " + request.email())));

            then(userService).should().createUser(request);
        }
    }

    @Nested
    @DisplayName("findById (GET /api/v1/users/{id})")
    class FindById {

        @Test
        @DisplayName("should return 200 OK and UserResponse when user exists")
        void shouldReturn200OkAndUserResponse_whenUserExists() throws Exception {
            UUID userId = UUID.randomUUID();
            Instant now = Instant.parse("2026-09-18T10:00:00Z");
            UserResponse response = new UserResponse(userId, "alice@example.com", UserStatus.ACTIVE, now, now);

            given(userService.findById(userId)).willReturn(response);

            mockMvc.perform(get(BASE_URL + "/{id}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(userId.toString())))
                    .andExpect(jsonPath("$.email", is("alice@example.com")))
                    .andExpect(jsonPath("$.status", is("ACTIVE")))
                    .andExpect(jsonPath("$.createdAt", is(now.toString())))
                    .andExpect(jsonPath("$.updatedAt", is(now.toString())));

            then(userService).should().findById(userId);
        }

        @Test
        @DisplayName("should return 404 Not Found when user does not exist")
        void shouldReturn404NotFound_whenUserDoesNotExist() throws Exception {
            UUID userId = UUID.randomUUID();

            given(userService.findById(userId))
                    .willThrow(new UserNotFoundException("User not found: " + userId));

            mockMvc.perform(get(BASE_URL + "/{id}", userId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("User Not Found")))
                    .andExpect(jsonPath("$.detail", is("User not found: " + userId)));

            then(userService).should().findById(userId);
        }
    }

    @Nested
    @DisplayName("findActiveByEmail (GET /api/v1/users?email={email})")
    class FindActiveByEmail {

        @Test
        @DisplayName("should return 200 OK and UserResponse when active user with email exists")
        void shouldReturn200OkAndUserResponse_whenActiveUserExists() throws Exception {
            String email = "alice@example.com";
            UUID userId = UUID.randomUUID();
            Instant now = Instant.parse("2026-09-18T10:00:00Z");
            UserResponse response = new UserResponse(userId, email, UserStatus.ACTIVE, now, now);

            given(userService.findActiveByEmail(email)).willReturn(response);

            mockMvc.perform(get(BASE_URL).param("email", email))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(userId.toString())))
                    .andExpect(jsonPath("$.email", is(email)))
                    .andExpect(jsonPath("$.status", is("ACTIVE")))
                    .andExpect(jsonPath("$.createdAt", is(now.toString())))
                    .andExpect(jsonPath("$.updatedAt", is(now.toString())));

            then(userService).should().findActiveByEmail(email);
        }

        @Test
        @DisplayName("should return 404 Not Found when no active user with email exists")
        void shouldReturn404NotFound_whenNoActiveUserExists() throws Exception {
            String email = "notfound@example.com";

            given(userService.findActiveByEmail(email))
                    .willThrow(new UserNotFoundException("Active user not found with email: " + email));

            mockMvc.perform(get(BASE_URL).param("email", email))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("User Not Found")))
                    .andExpect(jsonPath("$.detail", is("Active user not found with email: " + email)));

            then(userService).should().findActiveByEmail(email);
        }
    }

    @Nested
    @DisplayName("changePassword (PATCH /api/v1/users/{id}/password)")
    class ChangePassword {

        @Test
        @DisplayName("should return 204 No Content when password change succeeds")
        void shouldReturn204NoContent_whenPasswordChangeSucceeds() throws Exception {
            UUID userId = UUID.randomUUID();
            ChangePasswordRequest request = new ChangePasswordRequest("OldPassword1!", "NewPassword2!");

            willDoNothing().given(userService).changePassword(eq(userId), any(ChangePasswordRequest.class));

            mockMvc.perform(patch(BASE_URL + "/{id}/password", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            then(userService).should().changePassword(userId, request);
        }

        @Test
        @DisplayName("should return 400 Bad Request when request body fails validation")
        void shouldReturn400BadRequest_whenRequestBodyFailsValidation() throws Exception {
            UUID userId = UUID.randomUUID();
            ChangePasswordRequest invalidRequest = new ChangePasswordRequest("", "short");

            mockMvc.perform(patch(BASE_URL + "/{id}/password", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Failed")))
                    .andExpect(jsonPath("$.errors.currentPassword").exists())
                    .andExpect(jsonPath("$.errors.newPassword").exists());

            then(userService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should return 400 Bad Request when InvalidPasswordException is thrown")
        void shouldReturn400BadRequest_whenInvalidPasswordExceptionIsThrown() throws Exception {
            UUID userId = UUID.randomUUID();
            ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword!", "NewPassword2!");

            willThrow(new InvalidPasswordException("Current password is incorrect"))
                    .given(userService).changePassword(eq(userId), any(ChangePasswordRequest.class));

            mockMvc.perform(patch(BASE_URL + "/{id}/password", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Invalid Password")))
                    .andExpect(jsonPath("$.detail", is("Current password is incorrect")));

            then(userService).should().changePassword(userId, request);
        }

        @Test
        @DisplayName("should return 404 Not Found when user does not exist")
        void shouldReturn404NotFound_whenUserDoesNotExist() throws Exception {
            UUID userId = UUID.randomUUID();
            ChangePasswordRequest request = new ChangePasswordRequest("OldPassword1!", "NewPassword2!");

            willThrow(new UserNotFoundException("User not found: " + userId))
                    .given(userService).changePassword(eq(userId), any(ChangePasswordRequest.class));

            mockMvc.perform(patch(BASE_URL + "/{id}/password", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("User Not Found")))
                    .andExpect(jsonPath("$.detail", is("User not found: " + userId)));

            then(userService).should().changePassword(userId, request);
        }
    }

    @Nested
    @DisplayName("disableUser (DELETE /api/v1/users/{id})")
    class DisableUser {

        @Test
        @DisplayName("should return 204 No Content when disable succeeds")
        void shouldReturn204NoContent_whenDisableSucceeds() throws Exception {
            UUID userId = UUID.randomUUID();

            willDoNothing().given(userService).disableUser(userId);

            mockMvc.perform(delete(BASE_URL + "/{id}", userId))
                    .andExpect(status().isNoContent());

            then(userService).should().disableUser(userId);
        }

        @Test
        @DisplayName("should return 404 Not Found when user does not exist")
        void shouldReturn404NotFound_whenUserDoesNotExist() throws Exception {
            UUID userId = UUID.randomUUID();

            willThrow(new UserNotFoundException("User not found: " + userId))
                    .given(userService).disableUser(userId);

            mockMvc.perform(delete(BASE_URL + "/{id}", userId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("User Not Found")))
                    .andExpect(jsonPath("$.detail", is("User not found: " + userId)));

            then(userService).should().disableUser(userId);
        }
    }
}
