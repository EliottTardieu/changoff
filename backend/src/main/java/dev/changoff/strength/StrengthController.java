package dev.changoff.strength;

import dev.changoff.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StrengthController {

    private final StrengthService service;
    private final CurrentUser currentUser;

    public StrengthController(StrengthService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/exercises")
    public List<Ranking.Exercise> exercises(HttpServletRequest request) {
        currentUser.id(request);
        return service.exercises();
    }

    @GetMapping("/exercises/{id}/targets")
    public List<Ranking.Target> targets(
        @PathVariable String id,
        @RequestParam(defaultValue = "5") int reps,
        HttpServletRequest request
    ) {
        return service.targets(id, reps, currentUser.id(request));
    }

    @GetMapping("/lifts")
    public List<Map<String, Object>> lifts(HttpServletRequest request) {
        return service.lifts(currentUser.id(request));
    }

    @PostMapping("/lifts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> log(
        @Valid @RequestBody LiftInput input,
        HttpServletRequest request
    ) {
        return service.log(input, currentUser.id(request));
    }

    @DeleteMapping("/lifts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, HttpServletRequest request) {
        service.delete(id, currentUser.id(request));
    }

    @GetMapping("/dashboard")
    public List<Map<String, Object>> dashboard(HttpServletRequest request) {
        return service.dashboard(currentUser.id(request));
    }
}
