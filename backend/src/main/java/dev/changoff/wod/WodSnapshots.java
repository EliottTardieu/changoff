package dev.changoff.wod;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Snapshots preserve the prescription used for a saved workout when the catalog changes. */
@Component
public class WodSnapshots {

    private final ObjectMapper json;

    public WodSnapshots(ObjectMapper json) {
        this.json = json;
    }

    public String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to write WOD snapshot", exception);
        }
    }

    public <T> T decode(String text, Class<T> type) {
        try {
            return json.readValue(text, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read WOD snapshot", exception);
        }
    }
}
