package dev.romulus_lanceues.tanaw_api.shared.config;

import dev.romulus_lanceues.tanaw_api.auth.AuthController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenAPI Configuration Tests")
class OpenApiConfigTest {

    private final OpenApiConfig openApiConfig = new OpenApiConfig();

    @Test
    @DisplayName("OpenAPI bean defines Bearer JWT scheme and applies it globally")
    void openApiBean_hasBearerJwtSchemeAppliedGlobally() {
        OpenAPI openAPI = openApiConfig.openAPI();

        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Tanaw API");

        // Verify security scheme in components
        assertThat(openAPI.getComponents()).isNotNull();
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey(OpenApiConfig.BEARER_AUTH);

        SecurityScheme scheme = openAPI.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_AUTH);
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");

        // Verify applied globally
        assertThat(openAPI.getSecurity()).isNotEmpty();
        boolean hasGlobalBearer = openAPI.getSecurity().stream()
                .anyMatch(req -> req.containsKey(OpenApiConfig.BEARER_AUTH));
        assertThat(hasGlobalBearer).isTrue();
    }

    @Test
    @DisplayName("AuthController auth endpoints (register, login, refresh, logout) have security = {}")
    void authEndpoints_haveEmptySecurityRequirement() throws NoSuchMethodException {
        List<String> authMethodNames = List.of("register", "login", "refresh", "logout");

        for (Method method : AuthController.class.getDeclaredMethods()) {
            if (authMethodNames.contains(method.getName())) {
                Operation operation = method.getAnnotation(Operation.class);
                assertThat(operation)
                        .withFailMessage("Method %s must be annotated with @Operation", method.getName())
                        .isNotNull();
                assertThat(operation.security())
                        .withFailMessage("Method %s must have security = {}", method.getName())
                        .isEmpty();
            }
        }
    }
}
