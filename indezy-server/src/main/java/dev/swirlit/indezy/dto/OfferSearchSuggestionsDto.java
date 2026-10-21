package dev.swirlit.indezy.dto;

import lombok.Value;

import java.util.List;

/** The searches suggested to the workspace, best first, and the profile fields that would bring more. */
@Value
public class OfferSearchSuggestionsDto {

    /** What the profile can state to get suggestions from. */
    public enum ProfileField { SKILLS, POSITION, CITY, TARGET_RATE }

    List<OfferSearchSuggestionDto> suggestions;
    List<ProfileField> missing;
}
