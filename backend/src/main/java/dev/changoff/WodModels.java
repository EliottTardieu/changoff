package dev.changoff;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;

public final class WodModels {
    private WodModels() {}
    public record Exercise(String id,String name,String family,String category,String movementType,int difficulty,String unit,double secondsPerUnit,String note,List<String> equipment,List<String> patterns,List<String> muscles,Map<Integer,Dose> levels) {}
    public record Dose(int quantity,double weight) {}
    public record Restriction(@NotNull @Pattern(regexp="exercise|pattern|equipment|muscle") String kind,@NotBlank String target,@NotNull @Size(max=250) String reason,boolean active) {}
    public record Preferences(@Min(1) @Max(6) int level,@NotNull @Size(max=30) Set<String> equipment,@NotNull @Size(max=100) List<@NotNull @Valid Restriction> restrictions) {}
    public record Settings(@NotNull @Pattern(regexp="AUTO|AMRAP|EMOM|FOR_TIME|CHIPPER") String type,@NotNull @Pattern(regexp="balanced|engine|strength") String stimulus,@Min(1) @Max(6) int level,@Min(1) @Max(5) int intensity,@Min(5) @Max(60) int duration,@Min(3) @Max(8) int count,@Min(1) @Max(20) int rounds,@NotNull LocalDate scheduledOn,@NotNull @Size(max=30) Set<String> equipment,@NotNull @Size(max=100) Set<String> bannedExercises,@NotNull @Size(max=30) Set<String> bannedPatterns,@NotNull @Size(max=30) Set<String> bannedMuscles,@NotNull @Size(max=30) Set<String> bannedEquipment) {
        Settings withType(String resolved) {return new Settings(resolved,stimulus,level,intensity,duration,count,rounds,scheduledOn,equipment,bannedExercises,bannedPatterns,bannedMuscles,bannedEquipment);}
    }
    public record Prescription(@NotBlank String exerciseId,@Min(1) @Max(5000) int quantity,@DecimalMin("0") @DecimalMax("300") double weight) {}
    public record Generate(@NotNull @Valid Settings config,@Size(max=8) List<@NotNull @Valid Prescription> exercises) {}
    public record Item(Exercise exercise,int quantity,double weight,List<Prescription> alternatives) {}
    public record Draft(Settings config,List<Item> items,int rounds,double difficulty,String instructions,List<String> explanations) {}
    public record Save(@NotBlank @Size(max=100) String title,@NotNull @Valid Settings config,@NotNull @Size(min=3,max=8) List<@NotNull @Valid Prescription> exercises,@Min(1) Integer revision) {}
    public record Result(@NotNull @Pattern(regexp="planned|completed|skipped") String status,@NotNull LocalDate scheduledOn,@PastOrPresent LocalDate completedOn,@Min(1) @Max(86400) Integer durationSeconds,@Min(0) @Max(1000) Integer roundsCompleted,@Min(0) @Max(10000) Integer extraReps,@Min(1) @Max(10) Integer effort,@NotNull @Size(max=2000) String notes) {}
    public record Participant(@NotBlank @Email @Size(max=254) String email) {}
}
