package dev.changoff.cardio;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CardioInput(
    @NotNull @Pattern(regexp = "rope|running|cycling") String activity,
    @Min(1) @Max(2000000) Integer distanceMeters,
    @Min(1) @Max(1000000) Integer reps,
    @NotNull @DecimalMin("0.01") @DecimalMax("604800") Double durationSeconds,
    @NotNull @PastOrPresent LocalDate performedOn,
    @Size(max = 1000) String notes
) {}
