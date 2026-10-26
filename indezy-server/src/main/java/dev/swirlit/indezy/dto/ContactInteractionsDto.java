package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.InteractionChannel;

import java.time.LocalDate;
import java.util.List;

/**
 * A contact's exchanges, newest first, and what they say about the relationship.
 *
 * @param lastContactedOn the day of the latest exchange, null without any
 * @param asked           the exchanges the freelancer started whose answer is known
 * @param answered        those of them the contact answered
 * @param responseRate    {@code answered} out of {@code asked} in percent, null when {@code asked} is 0
 * @param preferredChannel the channel the contact answers or writes on most (the latest used on a tie), null without any
 */
public record ContactInteractionsDto(
    List<ContactInteractionDto> interactions,
    LocalDate lastContactedOn,
    int asked,
    int answered,
    Integer responseRate,
    InteractionChannel preferredChannel) {

    public ContactInteractionsDto {
        interactions = List.copyOf(interactions);
    }
}
