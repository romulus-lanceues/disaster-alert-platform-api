package dev.romulus_lanceues.tanaw_api.auth;


public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }

}
