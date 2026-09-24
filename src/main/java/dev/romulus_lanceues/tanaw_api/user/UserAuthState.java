package dev.romulus_lanceues.tanaw_api.user;

public record UserAuthState(
        boolean active,
        Long authenticationVersion
) {
    public UserAuthState(UserStatus status, Long authenticationVersion) {
        this(status == UserStatus.ACTIVE, authenticationVersion);
    }

    public boolean isActive() {
        return active;
    }
}
