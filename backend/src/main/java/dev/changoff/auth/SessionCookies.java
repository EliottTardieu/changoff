package dev.changoff.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class SessionCookies {

    private static final String NAME = "changoff_session";
    private final boolean secure;

    public SessionCookies(@Value("${app.secure-cookie}") boolean secure) {
        this.secure = secure;
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() != null) for (var cookie : request.getCookies())
            if (cookie.getName().equals(NAME)) return cookie.getValue();
        return "";
    }

    public void write(HttpServletResponse response, String token, Duration age) {
        response.addHeader(
            "Set-Cookie",
            ResponseCookie.from(NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api")
                .maxAge(age)
                .build()
                .toString()
        );
    }
}
