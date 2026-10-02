package dev.changoff;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.*;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.database-type=H2",
    }
)
@AutoConfigureMockMvc
class ApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void optionalRanksPreserveHistoryAndArePrivate() throws Exception {
        Cookie a = register("progress@example.com"),
            b = register("progress-other@example.com");
        mvc.perform(get("/api/me").cookie(a)).andExpect(jsonPath("ranks_enabled").value(true));
        mvc.perform(
            put("/api/me")
                .cookie(a)
                .header("X-Requested-With", "changoff")
                .contentType("application/json")
                .content(
                    """
                    {"name":"Alex","bodyweight":75,"standard":"male","ranksEnabled":false}
                    """
                )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("ranks_enabled").value(false));
        for (int reps : new int[] { 1, 20, 1000 }) {
            mvc.perform(
                post("/api/lifts")
                    .cookie(a)
                    .header("X-Requested-With", "changoff")
                    .contentType("application/json")
                    .content(
                        """
                        {"exercise":"bench-press","weight":75,"reps":%d,"performedOn":"2026-01-01"}
                        """.formatted(reps)
                    )
            ).andExpect(status().isCreated());
        }
        mvc.perform(get("/api/dashboard").cookie(a)).andExpect(
            jsonPath("$[0].rank").value("Silver 1")
        );
        mvc.perform(get("/api/lifts").cookie(a)).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/lifts").cookie(b)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/me").cookie(b)).andExpect(jsonPath("ranks_enabled").value(true));
        // Older clients omitting the preference must not reset it.
        mvc.perform(
            put("/api/me")
                .cookie(a)
                .header("X-Requested-With", "changoff")
                .contentType("application/json")
                .content(
                    """
                    {"name":"Alex","bodyweight":80,"standard":"female"}
                    """
                )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("ranks_enabled").value(false));
        for (int reps : new int[] { 0, 1001 }) {
            mvc.perform(
                post("/api/lifts")
                    .cookie(a)
                    .header("X-Requested-With", "changoff")
                    .contentType("application/json")
                    .content(
                        """
                        {"exercise":"bench-press","weight":75,"reps":%d,"performedOn":"2026-01-01"}
                        """.formatted(reps)
                    )
            ).andExpect(status().isBadRequest());
        }
        mvc.perform(
            put("/api/me")
                .cookie(a)
                .header("X-Requested-With", "changoff")
                .contentType("application/json")
                .content(
                    """
                    {"name":"Alex","bodyweight":75,"standard":"male","ranksEnabled":true}
                    """
                )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("ranks_enabled").value(true));
        mvc.perform(get("/api/lifts").cookie(a)).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/dashboard").cookie(a)).andExpect(
            jsonPath("$[0].rank").value("Silver 1")
        );
    }

    Cookie register(String email) throws Exception {
        var response = mvc
            .perform(
                post("/api/auth/register")
                    .header("X-Requested-With", "changoff")
                    .contentType("application/json")
                    .content(
                        """
                        {"name":"Alex","email":"%s","password":"test-password-123","bodyweight":75,"standard":"male"}
                        """.formatted(email)
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("password_hash").doesNotExist())
            .andReturn()
            .getResponse();
        String value = response.getHeader("Set-Cookie");
        assertTrue(value.contains("HttpOnly"));
        assertTrue(value.contains("SameSite=Strict"));
        return new Cookie("changoff_session", value.split(";", 2)[0].split("=", 2)[1]);
    }

    @Test
    void completePrivateTrainingLifecycle() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(
            post("/api/auth/register").contentType("application/json").content("{}")
        ).andExpect(status().isForbidden());
        Cookie a = register("alex@example.com"),
            b = register("other@example.com");
        mvc.perform(
            post("/api/auth/login")
                .header("X-Requested-With", "changoff")
                .contentType("application/json")
                .content("{\"email\":\"alex@example.com\",\"password\":\"incorrect\"}")
        ).andExpect(status().isUnauthorized());
        mvc.perform(
            post("/api/auth/login")
                .header("X-Requested-With", "changoff")
                .contentType("application/json")
                .content("{\"email\":\"ALEX@example.com\",\"password\":\"test-password-123\"}")
        ).andExpect(status().isOk());
        var result = mvc
            .perform(
                post("/api/lifts")
                    .cookie(a)
                    .header("X-Requested-With", "changoff")
                    .contentType("application/json")
                    .content(
                        "{\"exercise\":\"bench-press\",\"weight\":75,\"reps\":1,\"performedOn\":\"2026-01-01\"}"
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("rank").value("Silver 1"))
            .andReturn();
        String id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/lifts").cookie(b)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(
            delete("/api/lifts/" + id)
                .cookie(b)
                .header("X-Requested-With", "changoff")
        ).andExpect(status().isNotFound());
        mvc.perform(
            put("/api/me")
                .cookie(a)
                .header("X-Requested-With", "changoff")
                .contentType("application/json")
                .content("{\"name\":\"Alex\",\"bodyweight\":90,\"standard\":\"male\"}")
        ).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard").cookie(a)).andExpect(
            jsonPath("$[0].rank").value("Silver 1")
        );
        mvc.perform(get("/api/lifts").cookie(a)).andExpect(jsonPath("$[0].bodyweight").value(75));
        mvc.perform(get("/api/exercises/bench-press/targets?reps=13").cookie(a)).andExpect(
            status().isBadRequest()
        );
        mvc.perform(
            post("/api/lifts")
                .cookie(a)
                .header("X-Requested-With", "changoff")
                .contentType("application/json")
                .content(
                    "{\"exercise\":\"bench-press\",\"weight\":0,\"reps\":5,\"performedOn\":\"2026-01-01\"}"
                )
        ).andExpect(status().isBadRequest());
        mvc.perform(
            delete("/api/lifts/" + id)
                .cookie(a)
                .header("X-Requested-With", "changoff")
        ).andExpect(status().isNoContent());
        mvc.perform(get("/api/dashboard").cookie(a)).andExpect(
            jsonPath("$[0].rank").value("Unranked")
        );
        mvc.perform(
            post("/api/auth/logout").cookie(a).header("X-Requested-With", "changoff")
        ).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(a)).andExpect(status().isUnauthorized());
    }
}
