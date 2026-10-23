package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A French place resolved through the government geocoding API, cached so each city or postal code is
 * looked up once. Unknown places are cached too ({@code found} false) so they are not asked again.
 */
@Entity
@Table(name = "geo_places")
@Getter
@Setter
@NoArgsConstructor
public class GeoPlace {

    /** Folded "postal code|city" the place was looked up with. */
    @Id
    @Column(name = "query_key", length = 200)
    private String queryKey;

    @Column(name = "found", nullable = false)
    private boolean found;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "department_code", length = 3)
    private String departmentCode;

    /** Two digits for a region, three for an overseas collectivity ("978", Saint-Martin). */
    @Column(name = "region_code", length = 3)
    private String regionCode;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "resolved_at", nullable = false)
    private Instant resolvedAt;
}
