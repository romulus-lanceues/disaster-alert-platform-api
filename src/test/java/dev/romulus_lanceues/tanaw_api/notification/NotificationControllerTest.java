package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.alert.AlertStatus;
import dev.romulus_lanceues.tanaw_api.auth.JwtProperties;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAccessDeniedHandler;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAuthenticationEntryPoint;
import dev.romulus_lanceues.tanaw_api.auth.TestJwtFactory;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.shared.config.SecurityConfig;
import dev.romulus_lanceues.tanaw_api.shared.exception.GlobalExceptionHandler;
import dev.romulus_lanceues.tanaw_api.user.UserAuthState;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        ProblemDetailsAuthenticationEntryPoint.class,
        ProblemDetailsAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "JWT_SECRET=dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="
})
@DisplayName("NotificationController Tests")
class NotificationControllerTest {

    private static final String BASE_URL = "/api/v1/notifications";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private Clock clock;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private UserRepository userRepository;

    private String validToken;

    @BeforeEach
    void setUp() {
        TestJwtFactory jwtFactory = new TestJwtFactory(jwtProperties, clock);
        validToken = jwtFactory.createValidToken();

        given(userRepository.findAuthState(any(UUID.class)))
                .willReturn(Optional.of(new UserAuthState(UserStatus.ACTIVE, 0L)));
    }

    @Nested
    @DisplayName("getNotifications (GET /api/v1/notifications?userId={userId})")
    class GetNotifications {

        @Test
        @DisplayName("should return notifications for a user using default pagination and newest-first sorting")
        void shouldReturnNotificationsForUser_withDefaultPaginationAndNewestFirstSorting() throws Exception {
            UUID userId = UUID.randomUUID();
            NotificationResponse response = notificationResponse(UUID.randomUUID(), NotificationStatus.PENDING);

            given(notificationService.getNotificationsForUser(
                    eq(userId),
                    isNull(),
                    pageableMatching(0, 10)
            )).willReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1));

            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id", is(response.id().toString())))
                    .andExpect(jsonPath("$.content[0].status", is("PENDING")))
                    .andExpect(jsonPath("$.number", is(0)))
                    .andExpect(jsonPath("$.size", is(10)));

            then(notificationService).should().getNotificationsForUser(eq(userId), isNull(), pageableMatching(0, 10));
        }

        @Test
        @DisplayName("should pass the status filter and requested pagination to the service")
        void shouldPassStatusFilterAndRequestedPaginationToService() throws Exception {
            UUID userId = UUID.randomUUID();
            NotificationResponse response = notificationResponse(UUID.randomUUID(), NotificationStatus.SENT);

            given(notificationService.getNotificationsForUser(
                    eq(userId),
                    eq(NotificationStatus.SENT),
                    pageableMatching(1, 5)
            )).willReturn(new PageImpl<>(List.of(response), PageRequest.of(1, 5), 6));

            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString())
                            .param("status", "SENT")
                            .param("page", "1")
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].status", is("SENT")));

            then(notificationService).should().getNotificationsForUser(
                    eq(userId),
                    eq(NotificationStatus.SENT),
                    pageableMatching(1, 5)
            );
        }

        @Test
        @DisplayName("should return an empty page when the user has no notifications")
        void shouldReturnEmptyPage_whenUserHasNoNotifications() throws Exception {
            UUID userId = UUID.randomUUID();

            given(notificationService.getNotificationsForUser(eq(userId), isNull(), pageableMatching(0, 10)))
                    .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));

            then(notificationService).should().getNotificationsForUser(eq(userId), isNull(), pageableMatching(0, 10));
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid request parameters")
        void shouldReturnBadRequest_forInvalidRequestParameters() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", "not-a-uuid")
                            .param("status", "UNKNOWN")
                            .param("page", "-1")
                            .param("size", "11"))
                    .andExpect(status().isBadRequest());

            then(notificationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should return 400 Bad Request when userId is missing")
        void shouldReturnBadRequest_whenUserIdIsMissing() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                    .andExpect(status().isBadRequest());

            then(notificationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should return 400 Bad Request when the page size exceeds the maximum")
        void shouldReturnBadRequest_whenPageSizeExceedsMaximum() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", UUID.randomUUID().toString())
                            .param("size", "11"))
                    .andExpect(status().isBadRequest());

            then(notificationService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("getNotification (GET /api/v1/notifications/{id}?userId={userId})")
    class GetNotification {

        @Test
        @DisplayName("should return a notification owned by the user")
        void shouldReturnNotification_whenOwnedByUser() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID notificationId = UUID.randomUUID();
            NotificationResponse response = notificationResponse(notificationId, NotificationStatus.SENT);

            given(notificationService.getNotificationForUser(notificationId, userId)).willReturn(response);

            mockMvc.perform(get(BASE_URL + "/{id}", notificationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(notificationId.toString())))
                    .andExpect(jsonPath("$.status", is("SENT")));

            then(notificationService).should().getNotificationForUser(notificationId, userId);
        }

        @Test
        @DisplayName("should return 404 Not Found when the notification is missing or not owned by the user")
        void shouldReturnNotFound_whenNotificationIsMissingOrNotOwnedByUser() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID notificationId = UUID.randomUUID();

            given(notificationService.getNotificationForUser(notificationId, userId))
                    .willThrow(new NotificationNotFoundException("Notification not found: " + notificationId));

            mockMvc.perform(get(BASE_URL + "/{id}", notificationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Notification Not Found")))
                    .andExpect(jsonPath("$.detail", is("Notification not found: " + notificationId)));

            then(notificationService).should().getNotificationForUser(notificationId, userId);
        }

        @Test
        @DisplayName("should return 400 Bad Request when required UUID parameters are invalid or missing")
        void shouldReturnBadRequest_whenRequiredUuidParametersAreInvalidOrMissing() throws Exception {
            mockMvc.perform(get(BASE_URL + "/not-a-uuid")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                    .andExpect(status().isBadRequest());

            then(notificationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should return 400 Bad Request when userId is missing")
        void shouldReturnBadRequest_whenUserIdIsMissing() throws Exception {
            mockMvc.perform(get(BASE_URL + "/{id}", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                    .andExpect(status().isBadRequest());

            then(notificationService).shouldHaveNoInteractions();
        }
    }

    private static Pageable pageableMatching(int page, int size) {
        return argThat(pageable -> pageable.getPageNumber() == page
                && pageable.getPageSize() == size
                && pageable.getSort().getOrderFor("createdAt") != null
                && pageable.getSort().getOrderFor("createdAt").isDescending());
    }

    private static NotificationResponse notificationResponse(UUID notificationId, NotificationStatus status) {
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        return new NotificationResponse(
                notificationId,
                UUID.randomUUID(),
                NotificationChannel.EMAIL,
                "user@example.com",
                status,
                1,
                status == NotificationStatus.SENT ? now : null,
                null,
                now,
                now,
                AlertStatus.PENDING,
                UUID.randomUUID(),
                "Home",
                DisasterType.EARTHQUAKE,
                UUID.randomUUID(),
                now,
                5.2,
                "MODERATE",
                10.0,
                14.60,
                120.98
        );
    }
}
