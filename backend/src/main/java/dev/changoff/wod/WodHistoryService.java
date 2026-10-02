package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Draft;
import static dev.changoff.wod.WodModels.Generate;
import static dev.changoff.wod.WodModels.Item;
import static dev.changoff.wod.WodModels.Preferences;
import static dev.changoff.wod.WodModels.Result;
import static dev.changoff.wod.WodModels.Save;
import static dev.changoff.wod.WodModels.Settings;
import static dev.changoff.wod.WodValidation.conflict;
import static dev.changoff.wod.WodValidation.require;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WodHistoryService {

    private final WodRepository workouts;
    private final WodGenerationService generation;
    private final WodPreferenceService preferences;
    private final WodSnapshots snapshots;

    public WodHistoryService(
        WodRepository workouts,
        WodGenerationService generation,
        WodPreferenceService preferences,
        WodSnapshots snapshots
    ) {
        this.workouts = workouts;
        this.generation = generation;
        this.preferences = preferences;
        this.snapshots = snapshots;
    }

    public List<Map<String, Object>> history(UUID user) {
        return workouts.history(user);
    }

    void lockMember(UUID id, UUID user, boolean owner) {
        var rows = workouts.lockOwner(id);
        if (
            rows.isEmpty() || workouts.memberCount(id, user) == 0
        ) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "WOD not found.");
        if (owner && !rows.get().equals(user)) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Only the organizer can edit or add participants."
        );
    }

    @Transactional
    public Map<String, Object> save(UUID user, Save request, UUID existing) {
        Draft draft = generation.generate(
            user,
            new Generate(request.config(), request.exercises())
        );
        UUID id = existing == null ? UUID.randomUUID() : existing;
        if (existing == null) {
            workouts.insert(id, user, request, draft);
            workouts.addMember(id, user, draft.config().scheduledOn());
        } else {
            lockMember(id, user, true);
            require(request.revision() != null, "A revision is required when editing.");
            if (workouts.frozen(id)) throw conflict(
                "Completed WODs are immutable. Repeat it as a new WOD to make changes."
            );
            for (UUID participant : workouts.members(id))
                if (!participant.equals(user)) checkParticipant(participant, draft.items());
            int changed = workouts.update(id, request, draft);
            if (changed == 0) throw conflict(
                "This WOD changed in another session. Reload before editing."
            );
            workouts.schedule(id, user, draft.config().scheduledOn());
        }
        workouts.replaceSnapshots(id, draft);
        return detail(user, id);
    }

    public Map<String, Object> detail(UUID user, UUID id) {
        var rows = workouts.detailRows(user, id);
        if (rows.isEmpty()) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "WOD not found."
        );
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        result.put(
            "config",
            snapshots.decode((String) result.remove("config_json"), Settings.class)
        );
        result.put("items", workouts.items(id));
        result.put("isOwner", result.get("owner_id").equals(user));
        result.put(
            "canEdit",
            result.get("owner_id").equals(user) && !Boolean.TRUE.equals(result.get("frozen"))
        );
        // Other participants' personal results and notes are private.
        result.put("participants", workouts.participants(id));
        return result;
    }

    @Transactional
    public Map<String, Object> result(UUID user, UUID id, Result resultInput) {
        lockMember(id, user, false);
        require(
            !resultInput.status().equals("completed") || resultInput.completedOn() != null,
            "Choose a completion date."
        );
        require(
            resultInput.status().equals("completed") || resultInput.completedOn() == null,
            "Only completed WODs can have a completion date."
        );
        if (resultInput.status().equals("completed")) workouts.freeze(id);
        workouts.saveResult(id, user, resultInput);
        return detail(user, id);
    }

    void checkParticipant(UUID user, List<Item> items) {
        Preferences preferencesForUser = preferences.preferences(user);
        if (
            items
                .stream()
                .anyMatch(i -> !MovementPolicy.permitted(i.exercise(), preferencesForUser, null))
        ) throw conflict(
            "This WOD conflicts with a participant's saved equipment or restrictions. Adjust the WOD first."
        );
    }

    @Transactional
    public Map<String, Object> addParticipant(UUID user, UUID id, String email) {
        lockMember(id, user, true);
        var matches = workouts.findUserByEmail(email);
        if (matches.isEmpty()) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "No account matches that email."
        );
        UUID other = matches.getFirst();
        if (workouts.memberCount(id, other) > 0) throw conflict(
            "This user is already a participant."
        );
        if (workouts.participantCount(id) >= 20) throw conflict(
            "A WOD can have up to 20 participants."
        );
        checkParticipant(other, workouts.items(id));
        LocalDate date = workouts.scheduledDate(id, user);
        workouts.addMember(id, other, date);
        return detail(user, id);
    }

    @Transactional
    public void leave(UUID user, UUID id) {
        lockMember(id, user, false);
        UUID owner = workouts.owner(id);
        workouts.removeMember(id, user);
        var others = workouts.membersByJoinDate(id);
        if (others.isEmpty()) workouts.delete(id);
        else if (owner.equals(user)) workouts.transferOwnership(id, others.getFirst());
    }
}
