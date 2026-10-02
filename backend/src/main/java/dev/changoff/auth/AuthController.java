package dev.changoff.auth;

import static dev.changoff.account.AccountRequests.*;

import dev.changoff.account.AccountProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final SessionService sessions;
    private final SessionCookies cookies;

    public AuthController(AuthService auth, SessionService sessions, SessionCookies cookies) {
        this.auth = auth;
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountProfile register(
        @Valid @RequestBody Registration input,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        return complete(auth.register(input, cookies.read(request)), response);
    }

    @PostMapping("/login")
    public AccountProfile login(
        @Valid @RequestBody Login input,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        return complete(auth.login(input, cookies.read(request)), response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.revoke(cookies.read(request));
        cookies.write(response, "", Duration.ZERO);
    }

    private AccountProfile complete(
        AuthService.Authentication result,
        HttpServletResponse response
    ) {
        cookies.write(response, result.token(), SessionService.LIFETIME);
        return result.profile();
    }
}
