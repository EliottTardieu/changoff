package dev.changoff;

import static dev.changoff.wod.WodModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import dev.changoff.wod.*;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:wods;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    }
)
@AutoConfigureMockMvc
class WodTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    WodHistoryService history;

    @Autowired
    WodGenerationService generation;

    @Autowired
    WodPreferenceService preferences;

    @Autowired
    WodExposureService exposure;

    @Autowired
    WodCatalogRepository catalog;

    @Autowired
    JdbcTemplate db;

    record Person(UUID id, String email, Cookie cookie) {}

    JsonNode call(MockHttpServletRequestBuilder request, Person person, Object body, int status)
        throws Exception {
        request.header("X-Requested-With", "changoff");
        if (person != null) request.cookie(person.cookie());
        if (body != null) request
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        assertEquals(status, response.getStatus(), response.getContentAsString());
        return response.getContentAsString().isEmpty()
            ? mapper.nullNode()
            : mapper.readTree(response.getContentAsString());
    }

    Person person() throws Exception {
        String email = "wod-" + UUID.randomUUID() + "@example.com";
        var response = mvc
            .perform(
                post("/api/auth/register")
                    .header("X-Requested-With", "changoff")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        mapper.writeValueAsString(
                            Map.of(
                                "name",
                                "WOD Tester",
                                "email",
                                email,
                                "password",
                                "wod-test-password",
                                "bodyweight",
                                75,
                                "standard",
                                "male"
                            )
                        )
                    )
            )
            .andReturn()
            .getResponse();
        assertEquals(201, response.getStatus());
        return new Person(
            UUID.fromString(mapper.readTree(response.getContentAsString()).get("id").asText()),
            email,
            new Cookie(
                "changoff_session",
                response.getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1]
            )
        );
    }

    Settings settings(String type, Set<String> equipment, Set<String> muscles) {
        return new Settings(
            type,
            "balanced",
            3,
            3,
            20,
            3,
            3,
            LocalDate.now().plusDays(2),
            equipment,
            Set.of(),
            Set.of(),
            muscles,
            Set.of()
        );
    }

    List<Prescription> sets(Draft d) {
        return d
            .items()
            .stream()
            .map(i -> new Prescription(i.exercise().id(), i.quantity(), i.weight()))
            .toList();
    }

    Save save(Draft d, Integer revision) {
        return new Save("Test session", d.config(), sets(d), revision);
    }

    @Test
    void hardBansAndEquipmentApplyToGeneratedAndAlternativeMovements() throws Exception {
        Person p = person();
        preferences.savePreferences(
            p.id(),
            new Preferences(
                3,
                Set.of("dumbbells"),
                List.of(
                    new Restriction("exercise", "pushup", "Not today", true),
                    new Restriction("pattern", "jump", "No jumping", true)
                )
            )
        );
        for (int n = 0; n < 8; n++) {
            Draft d = generation.generate(
                p.id(),
                new Generate(settings("AMRAP", Set.of(), Set.of("quads")), null)
            );
            assertEquals(3, d.items().size());
            assertEquals(
                3,
                d
                    .items()
                    .stream()
                    .map(i -> i.exercise().id())
                    .distinct()
                    .count()
            );
            for (Item i : d.items()) {
                assertTrue(i.exercise().equipment().isEmpty());
                assertFalse(i.exercise().muscles().contains("quads"));
                assertFalse(i.exercise().patterns().contains("jump"));
                assertNotEquals("pushup", i.exercise().id());
                for (Prescription a : i.alternatives()) {
                    var e = catalog
                        .exercises()
                        .stream()
                        .filter(x -> x.id().equals(a.exerciseId()))
                        .findFirst()
                        .orElseThrow();
                    assertTrue(e.equipment().isEmpty());
                    assertFalse(e.muscles().contains("quads"));
                    assertFalse(e.patterns().contains("jump"));
                }
            }
        }
        Preferences persisted = preferences.preferences(p.id());
        assertEquals(2, persisted.restrictions().size());
        assertEquals(Set.of("dumbbells"), persisted.equipment());
        var allMuscles = new HashSet<>(
            db.queryForList("SELECT id FROM muscle_group", String.class)
        );
        var failure = assertThrows(ResponseStatusException.class, () ->
            generation.generate(p.id(), new Generate(settings("AMRAP", Set.of(), allMuscles), null))
        );
        assertEquals(409, failure.getStatusCode().value());
    }

    @Test
    void eachFormatHasCorrectSemanticsAndAutomaticTypeCanVary() throws Exception {
        Person p = person();
        Set<String> equipment = new HashSet<>(
            db.queryForList("SELECT id FROM equipment", String.class)
        );
        for (String type : List.of("AMRAP", "EMOM", "FOR_TIME", "CHIPPER")) {
            Draft d = generation.generate(
                p.id(),
                new Generate(settings(type, equipment, Set.of()), null)
            );
            assertEquals(type, d.config().type());
            assertTrue(d.difficulty() >= 1 && d.difficulty() <= 10);
            if (type.equals("CHIPPER")) assertEquals(1, d.rounds());
            if (type.equals("AMRAP")) assertEquals(0, d.rounds());
            if (type.equals("FOR_TIME")) assertEquals(3, d.rounds());
            if (type.equals("EMOM")) for (Item i : d.items())
                assertTrue(i.quantity() * i.exercise().secondsPerUnit() <= 40);
        }
        Set<String> types = new HashSet<>();
        for (int n = 0; n < 4; n++) {
            Draft d = generation.generate(
                p.id(),
                new Generate(settings("AUTO", equipment, Set.of()), null)
            );
            types.add(d.config().type());
            history.save(p.id(), save(d, null), null);
        }
        assertEquals(4, types.size());
    }

    @Test
    void sharingResultsIsolationLeavingAndFrozenSnapshots() throws Exception {
        Person owner = person(),
            partner = person(),
            outsider = person();
        Draft d = generation.generate(
            owner.id(),
            new Generate(settings("FOR_TIME", Set.of(), Set.of()), null)
        );
        JsonNode saved = call(post("/api/wods"), owner, save(d, null), 201);
        UUID id = UUID.fromString(saved.get("id").asText());
        call(get("/api/wods/" + id), outsider, null, 404);
        call(put("/api/wods/" + id), outsider, save(d, 1), 404);
        call(
            post("/api/wods/" + id + "/participants"),
            owner,
            new Participant(partner.email()),
            200
        );
        assertEquals(1, call(get("/api/wods"), partner, null, 200).size());
        call(
            post("/api/wods/" + id + "/participants"),
            partner,
            new Participant(outsider.email()),
            403
        );
        Result result = new Result(
            "completed",
            LocalDate.now(),
            LocalDate.now(),
            420,
            3,
            0,
            7,
            "Personal note"
        );
        call(put("/api/wods/" + id + "/result"), partner, result, 200);
        JsonNode own = call(get("/api/wods/" + id), owner, null, 200);
        assertEquals("planned", own.get("status").asText());
        assertEquals("", own.get("notes").asText());
        assertFalse(own.get("canEdit").asBoolean());
        assertEquals(
            0,
            call(get("/api/wods/statistics"), owner, null, 200).get("completed").asInt()
        );
        JsonNode stats = call(get("/api/wods/statistics"), partner, null, 200);
        assertEquals(1, stats.get("completed").asInt());
        assertEquals(7, stats.get("loggedMinutes").asInt());
        assertEquals(7, stats.get("averageEffort").asInt());
        call(put("/api/wods/" + id), owner, save(d, 1), 409);
        call(
            put("/api/wods/" + id + "/result"),
            partner,
            new Result("planned", LocalDate.now(), null, null, null, null, null, ""),
            200
        );
        call(put("/api/wods/" + id), owner, save(d, 1), 409); // Completion permanently freezes the shared prescription.
        call(delete("/api/wods/" + id + "/participation"), partner, null, 204);
        call(get("/api/wods/" + id), partner, null, 404);
        call(put("/api/wods/" + id + "/result"), partner, result, 404);
        assertEquals(1, call(get("/api/wods"), owner, null, 200).size());
        call(delete("/api/wods/" + id + "/participation"), owner, null, 204);
        assertEquals(
            0,
            db.queryForObject("SELECT COUNT(*) FROM wods WHERE id=?", Integer.class, id)
        );
    }

    @Test
    void ownerCanLeaveWithoutDestroyingOtherMembersAndStaleEditsFail() throws Exception {
        Person a = person(),
            b = person();
        Draft d = generation.generate(
            a.id(),
            new Generate(settings("EMOM", Set.of(), Set.of()), null)
        );
        var first = history.save(a.id(), save(d, null), null);
        UUID id = (UUID) first.get("id");
        history.save(a.id(), save(d, 1), id);
        call(put("/api/wods/" + id), a, save(d, 1), 409);
        history.addParticipant(a.id(), id, b.email());
        history.leave(a.id(), id);
        assertTrue((Boolean) history.detail(b.id(), id).get("isOwner"));
        assertEquals(1, history.history(b.id()).size());
        assertThrows(ResponseStatusException.class, () -> history.detail(a.id(), id));
    }

    @Test
    void validationAndTamperedPrescriptionsCannotBypassBans() throws Exception {
        Person p = person();
        call(
            post("/api/wods/generate"),
            null,
            new Generate(settings("AMRAP", Set.of(), Set.of()), null),
            401
        );
        var c = settings("AMRAP", Set.of(), Set.of("quads"));
        call(
            post("/api/wods/generate"),
            p,
            new Generate(
                c,
                List.of(
                    new Prescription("air-squat", 10, 0),
                    new Prescription("pushup", 5, 0),
                    new Prescription("plank", 20, 0)
                )
            ),
            400
        );
        call(
            post("/api/wods/generate"),
            p,
            new Generate(
                settings("EMOM", Set.of(), Set.of()),
                List.of(
                    new Prescription("pushup", 200, 0),
                    new Prescription("plank", 20, 0),
                    new Prescription("dead-bug", 10, 0)
                )
            ),
            400
        );
        call(post("/api/wods/generate"), p, Map.of("config", Map.of("type", "UNKNOWN")), 400);
        call(
            put("/api/wods/preferences"),
            p,
            new Preferences(3, Set.of("imaginary"), List.of()),
            400
        );
        Draft d = generation.generate(
            p.id(),
            new Generate(settings("AMRAP", Set.of(), Set.of()), null)
        );
        UUID id = (UUID) history.save(p.id(), save(d, null), null).get("id");
        call(
            put("/api/wods/" + id + "/result"),
            p,
            new Result("completed", LocalDate.now(), LocalDate.now().plusDays(1), 20, 0, 0, 5, ""),
            400
        );
        call(
            put("/api/wods/" + id + "/result"),
            p,
            new Result("completed", LocalDate.now(), null, 20, 0, 0, 5, ""),
            400
        );
    }

    @Test
    void sharedWorkoutChecksRecipientsRestrictionsAndHistoryIsUserScoped() throws Exception {
        Person a = person(),
            b = person();
        Draft d = generation.generate(
            a.id(),
            new Generate(settings("AMRAP", Set.of(), Set.of()), null)
        );
        UUID id = (UUID) history.save(a.id(), save(d, null), null).get("id");
        preferences.savePreferences(
            b.id(),
            new Preferences(
                1,
                Set.of(),
                List.of(
                    new Restriction(
                        "exercise",
                        d.items().getFirst().exercise().id(),
                        "Excluded",
                        true
                    )
                )
            )
        );
        assertThrows(ResponseStatusException.class, () ->
            history.addParticipant(a.id(), id, b.email())
        );
        assertTrue(history.history(b.id()).isEmpty());
        assertFalse(exposure.exposure(a.id(), LocalDate.now().plusDays(2)).isEmpty());
        assertTrue(exposure.exposure(b.id(), LocalDate.now().plusDays(2)).isEmpty());
    }
}
