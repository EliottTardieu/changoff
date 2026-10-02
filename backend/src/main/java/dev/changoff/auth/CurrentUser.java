package dev.changoff.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** HTTP adapter shared by controllers; application services receive user IDs. */
@Component
public class CurrentUser {

    private final SessionService sessions;
    private final SessionCookies cookies;

    public CurrentUser(SessionService sessions, SessionCookies cookies) {
        this.sessions = sessions;
        this.cookies = cookies;
    }

    public UUID id(HttpServletRequest request) {
        return sessions.requireUser(cookies.read(request));
    }
}
