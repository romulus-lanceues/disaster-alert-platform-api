package dev.romulus_lanceues.tanaw_api.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieHelper {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    public static final String AUTH_PATH = "/api/v1/auth";

    private final JwtProperties jwtProperties;

    public ResponseCookie createRefreshTokenCookie(String rawRefreshToken) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(AUTH_PATH)
                .maxAge(jwtProperties.refreshTokenDuration())
                .build();
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(AUTH_PATH)
                .maxAge(0)
                .build();
    }
}
