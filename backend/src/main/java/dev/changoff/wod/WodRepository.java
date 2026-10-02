package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Draft;
import static dev.changoff.wod.WodModels.Item;
import static dev.changoff.wod.WodModels.Result;
import static dev.changoff.wod.WodModels.Save;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence contract; the JDBC implementation owns SQL and row mapping. */
public interface WodRepository {
    List<Map<String, Object>> exposureRows(UUID user, LocalDate planned);
    List<String> recentTypes(UUID user);
    Optional<UUID> lockOwner(UUID id);
    int memberCount(UUID id, UUID user);
    void insert(UUID id, UUID user, Save request, Draft draft);
    void addMember(UUID id, UUID user, LocalDate date);
    boolean frozen(UUID id);
    List<UUID> members(UUID id);
    int update(UUID id, Save request, Draft draft);
    void schedule(UUID id, UUID user, LocalDate date);
    List<Map<String, Object>> detailRows(UUID user, UUID id);
    List<Map<String, Object>> participants(UUID id);
    void freeze(UUID id);
    void saveResult(UUID id, UUID user, Result r);
    List<UUID> findUserByEmail(String email);
    int participantCount(UUID id);
    LocalDate scheduledDate(UUID id, UUID user);
    UUID owner(UUID id);
    void removeMember(UUID id, UUID user);
    List<UUID> membersByJoinDate(UUID id);
    void delete(UUID id);
    void transferOwnership(UUID id, UUID owner);
    List<Map<String, Object>> completed(UUID user, LocalDate start, LocalDate end);
    int completedInWeek(UUID user, LocalDate from);
    int upcoming(UUID user, LocalDate today);
    List<Map<String, Object>> history(UUID user);
    List<Item> items(UUID id);
    void replaceSnapshots(UUID id, Draft d);
}
