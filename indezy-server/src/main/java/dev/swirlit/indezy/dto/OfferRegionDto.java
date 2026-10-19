package dev.swirlit.indezy.dto;

import java.util.List;

/** A French region and its departments, for the feed's location filter. */
public record OfferRegionDto(String regionCode, String name, List<Department> departments) {

    public record Department(String departmentCode, String name) {
    }

    public OfferRegionDto {
        departments = List.copyOf(departments);
    }
}
