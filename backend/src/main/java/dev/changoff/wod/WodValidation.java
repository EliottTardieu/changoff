package dev.changoff.wod;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class WodValidation {

    private WodValidation() {}

    static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
