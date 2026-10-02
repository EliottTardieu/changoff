package dev.changoff.account;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public AccountProfile profile(UUID user) {
        return accounts.profile(user);
    }

    @Transactional
    public AccountProfile update(UUID user, AccountRequests.Profile input) {
        accounts.update(user, input);
        return accounts.profile(user);
    }
}
