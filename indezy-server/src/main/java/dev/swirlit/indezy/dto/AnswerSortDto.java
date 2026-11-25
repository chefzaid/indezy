package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.ReplyKind;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * The freelancer's own sort of an answer received, in place of the AI's: what it says, and for a "not now" one the day
 * to write back (three months later when none is given).
 */
public record AnswerSortDto(@NotNull ReplyKind replyKind, LocalDate writeBackOn) {
}
