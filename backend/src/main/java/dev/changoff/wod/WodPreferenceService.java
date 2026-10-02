package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Preferences;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WodPreferenceService {

    private final WodPreferenceRepository preferences;
    private final WodCatalogRepository catalog;

    public WodPreferenceService(WodPreferenceRepository preferences, WodCatalogRepository catalog) {
        this.preferences = preferences;
        this.catalog = catalog;
    }

    public Preferences preferences(UUID user) {
        return preferences.preferences(user);
    }

    @Transactional
    public Preferences savePreferences(UUID user, Preferences p) {
        catalog.validateIds(p.equipment(), "equipment");
        for (var r : p.restrictions())
            catalog.validateIds(
                Set.of(r.target()),
                switch (r.kind()) {
                    case "exercise" -> "wod_exercise";
                    case "pattern" -> "movement_pattern";
                    case "muscle" -> "muscle_group";
                    default -> "equipment";
                }
            );
        preferences.replace(user, p);
        return preferences(user);
    }
}
