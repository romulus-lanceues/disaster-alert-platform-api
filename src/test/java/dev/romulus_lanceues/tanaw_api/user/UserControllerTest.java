package dev.romulus_lanceues.tanaw_api.user;

import dev.romulus_lanceues.tanaw_api.auth.JwtProperties;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAccessDeniedHandler;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAuthenticationEntryPoint;
import dev.romulus_lanceues.tanaw_api.auth.TestJwtFactory;
import dev.romulus_lanceues.tanaw_api.shared.config.SecurityConfig;
import dev.romulus_lanceues.tanaw_api.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        ProblemDetailsAuthenticationEntryPoint.class,
        ProblemDetailsAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "JWT_SECRET=dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="
})
@DisplayName("UserController Tests")
class UserControllerTest {

    private static final String BASE_URL = "/api/v1/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private Clock clock;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    private TestJwtFactory jwtFactory;

    @BeforeEach
    void setUp() {
        jwtFactory = new TestJwtFactory(jwtProperties, clock);

        given(userRepository.findAuthState(any(UUID.class)))
                .willReturn(Optional.of(new UserAuthState(UserStatus.ACTIVE, 0L)));
    }

    @Nested
    @DisplayName("getCurrentUser (GET /api/v1/users/me)")
    class GetCurrentUser {

        @Test
        @DisplayName("should return 200 OK and caller's UserResponse derived from JWT subject")
        void shouldReturn200OkAndUserResponse_whenUserExists() throws Exception {
            UUID userId = UUID.randomUUID();
            String token = jwtFactory.createValidToken(userId);
            Instant now = Instant.parse("2026-09-18T10:00:00Z");
            UserResponse response = new UserResponse(userId, "alice@example.com", UserStatus.ACTIVE, now, now);

            given(userService.findById(userId)).willReturn(response);

            mockMvc.perform(get(BASE_URL + "/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(userId.toString())))
                    .andExpect(jsonPath("$.email", is("alice@example.com")))
                    .andExpect(jsonPath("$.status", is("ACTIVE")))
                    .andExpect(jsonPath("$.createdAt", is(now.toString())))
                    .andExpect(jsonPath("$.updatedAt", is(now.toString())));

            then(userService).should().findById(userId);
        }

        @Test
        @DisplayName("should return 404 Not Found when authenticated user does not exist in service")
        void shouldReturn404NotFound_whenUserDoesNotExist() throws Exception {
            UUID userId = UUID.randomUUID();
            String token = jwtFactory.createValidToken(userId);

            given(userService.findById(userId))
                    .willThrow(new UserNotFoundException("User not found: " + userId));

            mockMvc.perform(get(BASE_URL + "/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("User Not Found")))
                    .andExpect(jsonPath("$.detail", is("User not found: " + userId)));

            then(userService).should().findById(userId);
        }
    }

    @Nested
    @DisplayName("changePassword (PATCH /api/v1/users/me/password)")
    class ChangePassword {

        @Test
        @DisplayName("should return 204 No Content when password change succeeds")
        void shouldReturn204NoContent_whenPasswordChangeSucceeds() throws Exception {
            UUID userId = UUID.randomUUID();
            String token = jwtFactory.createValidToken(userId);
            ChangePasswordRequest request = new ChangePasswordRequest("OldPassword1!", "NewPassword2!");

            willDoNothing().given(userService).changePassword(eq(userId), any(ChangePasswordRequest.class));

            mockMvc.perform(patch(BASE_URL + "/me/password")
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            then(userService).should().changePassword(userId, request);
        }

        @Test
        @DisplayName("should return 400 Bad Request when request body fails validation")
        void shouldReturn400BadRequest_whenRequestBodyFailsValidation() throws Exception {
            UUID userId = UUID.randomUUID();
            String token = jwtFactory.createValidToken(userId);
            ChangePasswordRequest invalidRequest = new ChangePasswordRequest("", "short");

            mockMvc.perform(patch(BASE_URL + "/me/password")
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
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
            String token = jwtFactory.createValidToken(userId);
            ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword!", "NewPassword2!");

            willThrow(new InvalidPasswordException("Current password is incorrect"))
                    .given(userService).changePassword(eq(userId), any(ChangePasswordRequest.class));

            mockMvc.perform(patch(BASE_URL + "/me/password")
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
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
            String token = jwtFactory.createValidToken(userId);
            ChangePasswordRequest request = new ChangePasswordRequest("OldPassword1!", "NewPassword2!");

            willThrow(new UserNotFoundException("User not found: " + userId))
                    .given(userService).changePassword(eq(userId), any(ChangePasswordRequest.class));

            mockMvc.perform(patch(BASE_URL + "/me/password")
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("User Not Found")))
                    .andExpect(jsonPath("$.detail", is("User not found: " + userId)));

            then(userService).should().changePassword(userId, request);
        }
    }

    @Nested
    @DisplayName("disableCurrentUser (DELETE /api/v1/users/me)")
    class DisableCurrentUser {

        @Test
        @DisplayName("should return 204 No Content when disable succeeds on DELETE /me")
        void shouldReturn204NoContent_whenDisableSucceeds() throws Exception {
            UUID userId = UUID.randomUUID();
            String token = jwtFactory.createValidToken(userId);

            willDoNothing().given(userService).disableUser(userId);

            mockMvc.perform(delete(BASE_URL + "/me")
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNoContent());

            then(userService).should().disableUser(userId);
        }

        @Test
        @DisplayName("should return 404 Not Found when user does not exist on DELETE /me")
        void shouldReturn404NotFound_whenUserDoesNotExist() throws Exception {
            UUID userId = UUID.randomUUID();
            String token = jwtFactory.createValidToken(userId);

            willThrow(new UserNotFoundException("User not found: " + userId))
                    .given(userService).disableUser(userId);

            mockMvc.perform(delete(BASE_URL + "/me")
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("User Not Found")))
                    .andExpect(jsonPath("$.detail", is("User not found: " + userId)));

            then(userService).should().disableUser(userId);
        }
    }
}
