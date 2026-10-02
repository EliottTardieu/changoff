package dev.changoff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

import dev.changoff.account.AccountRequests.Registration;
import dev.changoff.auth.AuthService;
import dev.changoff.auth.SessionRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:registration-rollback;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    }
)
class RegistrationTransactionTest {

    @Autowired
    AuthService authentication;

    @Autowired
    JdbcTemplate database;

    @MockitoBean
    SessionRepository sessions;

    @Test
    void sessionStorageFailureDoesNotLeaveAnUnusableNewAccount() {
        doThrow(new DataAccessResourceFailureException("Session storage unavailable"))
            .when(sessions)
            .insert(anyString(), any(UUID.class), any(Instant.class));
        var registration = new Registration(
            "Rollback",
            "rollback@example.com",
            "valid-password-123",
            75.0,
            "male"
        );

        assertThrows(DataAccessResourceFailureException.class, () ->
            authentication.register(registration, "")
        );
        assertEquals(
            0,
            database.queryForObject(
                "SELECT COUNT(*) FROM athletes WHERE email=?",
                Integer.class,
                registration.email()
            )
        );
    }
}
