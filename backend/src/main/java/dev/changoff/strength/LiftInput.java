package dev.changoff.strength;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

public record LiftInput(
    @NotBlank String exercise,
    @NotNull @DecimalMin("0") @DecimalMax("1500") Double weight,
    @Min(1) @Max(1000) int reps,
    @NotNull @PastOrPresent LocalDate performedOn
) {}
