package dev.changoff.account;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AccountRequests {

    private AccountRequests() {}

    public record Registration(
        @NotBlank @Size(max = 60) String name,
        @Email @NotBlank @Size(max = 254) String email,
        @NotNull @Size(min = 10, max = 72) String password,
        @NotNull @DecimalMin("30") @DecimalMax("300") Double bodyweight,
        @Pattern(regexp = "male|female") @NotNull String standard
    ) {}

    public record Login(@NotBlank @Email String email, @NotNull @Size(max = 72) String password) {}

    public record Profile(
        @NotBlank @Size(max = 60) String name,
        @NotNull @DecimalMin("30") @DecimalMax("300") Double bodyweight,
        @NotNull @Pattern(regexp = "male|female") String standard,
        Boolean ranksEnabled
    ) {}
}
