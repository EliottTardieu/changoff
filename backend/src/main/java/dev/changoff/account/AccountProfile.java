package dev.changoff.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** The JSON field names are an existing public API contract. */
public record AccountProfile(
    UUID id,
    String name,
    String email,
    double bodyweight,
    String standard,
    @JsonProperty("ranks_enabled") boolean ranksEnabled
) {}
