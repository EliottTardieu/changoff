package dev.changoff.web;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate database;

    public HealthController(JdbcTemplate database) {
        this.database = database;
    }

    @GetMapping
    public Map<String, String> health() {
        database.queryForObject("SELECT 1", Integer.class);
        return Map.of("status", "UP");
    }
}
