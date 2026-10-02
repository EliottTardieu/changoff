package dev.changoff.account;

import dev.changoff.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class AccountController {

    private final AccountService accounts;
    private final CurrentUser currentUser;

    public AccountController(AccountService accounts, CurrentUser currentUser) {
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    @GetMapping
    public AccountProfile profile(HttpServletRequest request) {
        return accounts.profile(currentUser.id(request));
    }

    @PutMapping
    public AccountProfile update(
        @Valid @RequestBody AccountRequests.Profile input,
        HttpServletRequest request
    ) {
        return accounts.update(currentUser.id(request), input);
    }
}
