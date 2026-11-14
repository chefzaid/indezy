package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.ProjectStatus;

import java.util.List;

/**
 * The opportunities on a map (see docs/features.md#opportunity-map): the home address and each opportunity's place of
 * work or client address that the address search found; {@code home} is null without a home address, and
 * {@code unplaced} counts the opportunities whose place could not be found.
 */
public record OpportunityMapDto(Point home, List<Pin> pins, int unplaced) {

    public OpportunityMapDto {
        pins = List.copyOf(pins);
    }

    public record Point(double latitude, double longitude) {
    }

    /** An opportunity at its place; opportunities at the same place share its point. */
    public record Pin(Long projectId, String role, String clientName, ProjectStatus status, String place, double latitude, double longitude) {
    }

    // Spring MVC logs bodies through toString at DEBUG: no address.
    @Override
    public String toString() {
        return "OpportunityMapDto[pins=" + pins.size() + "]";
    }
}
