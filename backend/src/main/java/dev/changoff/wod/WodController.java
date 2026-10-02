package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Draft;
import static dev.changoff.wod.WodModels.Generate;
import static dev.changoff.wod.WodModels.Participant;
import static dev.changoff.wod.WodModels.Preferences;
import static dev.changoff.wod.WodModels.Result;
import static dev.changoff.wod.WodModels.Save;

import dev.changoff.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wods")
public class WodController {

    private final CurrentUser auth;
    private final WodHistoryService history;
    private final WodGenerationService generation;
    private final WodPreferenceService preferences;
    private final WodStatisticsService statistics;
    private final WodCatalogRepository catalog;

    public WodController(
        CurrentUser auth,
        WodHistoryService history,
        WodGenerationService generation,
        WodPreferenceService preferences,
        WodStatisticsService statistics,
        WodCatalogRepository catalog
    ) {
        this.auth = auth;
        this.history = history;
        this.generation = generation;
        this.preferences = preferences;
        this.statistics = statistics;
        this.catalog = catalog;
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog(HttpServletRequest r) {
        auth.id(r);
        return catalog.all();
    }

    @GetMapping("/preferences")
    public Preferences preferences(HttpServletRequest r) {
        return preferences.preferences(auth.id(r));
    }

    @PutMapping("/preferences")
    public Preferences preferences(@Valid @RequestBody Preferences p, HttpServletRequest r) {
        return preferences.savePreferences(auth.id(r), p);
    }

    @PostMapping("/generate")
    public Draft generate(@Valid @RequestBody Generate p, HttpServletRequest r) {
        return generation.generate(auth.id(r), p);
    }

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest r) {
        return history.history(auth.id(r));
    }

    @GetMapping("/statistics")
    public Map<String, Object> statistics(HttpServletRequest r) {
        return statistics.statistics(auth.id(r));
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Map<String, Object> save(@Valid @RequestBody Save p, HttpServletRequest r) {
        return history.save(auth.id(r), p, null);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable UUID id, HttpServletRequest r) {
        return history.detail(auth.id(r), id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> edit(
        @PathVariable UUID id,
        @Valid @RequestBody Save p,
        HttpServletRequest r
    ) {
        return history.save(auth.id(r), p, id);
    }

    @PutMapping("/{id}/result")
    public Map<String, Object> result(
        @PathVariable UUID id,
        @Valid @RequestBody Result p,
        HttpServletRequest r
    ) {
        return history.result(auth.id(r), id, p);
    }

    @PostMapping("/{id}/participants")
    public Map<String, Object> share(
        @PathVariable UUID id,
        @Valid @RequestBody Participant p,
        HttpServletRequest r
    ) {
        return history.addParticipant(auth.id(r), id, p.email());
    }

    @DeleteMapping("/{id}/participation")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void leave(@PathVariable UUID id, HttpServletRequest r) {
        history.leave(auth.id(r), id);
    }
}
