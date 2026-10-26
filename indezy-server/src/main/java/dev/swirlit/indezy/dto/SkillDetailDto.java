package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.swirlit.indezy.model.enums.SkillLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** A profile skill's level and years of experience, each optional. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SkillDetailDto(SkillLevel level, @Min(0) @Max(60) Integer years) {
}
