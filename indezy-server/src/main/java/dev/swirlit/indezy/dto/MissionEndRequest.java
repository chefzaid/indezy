package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Ends a won mission: the day it ended and why. */
public record MissionEndRequest(@NotNull LocalDate endedOn, @NotBlank @Size(max = 2000) String reason) {
}
