package dev.changoff.cardio;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cardio")
public class CardioController {

    private final CardioService service;
    private final CurrentUser currentUser;

    public CardioController(CardioService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/benchmarks")
    public List<CardioRanking.Benchmark> benchmarks(HttpServletRequest request) {
        currentUser.id(request);
        return service.benchmarks();
    }

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest request) {
        return service.history(currentUser.id(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> save(
        @Valid @RequestBody CardioInput input,
        HttpServletRequest request
    ) {
        return service.save(input, currentUser.id(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, HttpServletRequest request) {
        service.delete(id, currentUser.id(request));
    }
}
