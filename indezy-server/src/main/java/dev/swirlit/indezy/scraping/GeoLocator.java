package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.GeoPlace;
import dev.swirlit.indezy.repository.GeoPlaceRepository;
import dev.swirlit.indezy.util.TextFolding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Locates French cities through the government geocoding API ({@code geo.api.gouv.fr}): coordinates,
 * department and region of a commune, looked up by name and optional postal code. Every answer, unknown
 * places included, is cached in {@link GeoPlace}, so a place is asked once.
 */
@Slf4j
@Component
public class GeoLocator {

    public record Place(String city, String departmentCode, String regionCode, double latitude, double longitude) {
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final GeoPlaceRepository places;
    private final OfferProperties properties;
    private final HttpClient http;
    private final Clock clock;

    @Autowired
    public GeoLocator(GeoPlaceRepository places, OfferProperties properties) {
        this(places, properties, HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), Clock.systemUTC());
    }

    GeoLocator(GeoPlaceRepository places, OfferProperties properties, HttpClient http, Clock clock) {
        this.places = places;
        this.properties = properties;
        this.http = http;
        this.clock = clock;
    }

    /** The commune named {@code city} (in {@code postalCode} when known); empty when unknown or not looked up. */
    public Optional<Place> locate(String city, String postalCode) {
        String name = TextFolding.words(city);
        String code = postalCode != null && postalCode.trim().matches("\\d{5}") ? postalCode.trim() : null;
        if ((name == null || name.isEmpty()) && code == null) {
            return Optional.empty();
        }
        String key = (code == null ? "" : code) + "|" + (name == null ? "" : name);
        Optional<GeoPlace> cached = places.findById(key);
        if (cached.isPresent()) {
            return toPlace(cached.get());
        }
        if (!properties.geocodingEnabled()) {
            return Optional.empty();
        }
        try {
            GeoPlace place = lookUp(key, city, code);
            // "Paris 75000": a generic postal code matches no commune, the name alone does.
            if (!place.isFound() && code != null && name != null && !name.isEmpty()) {
                GeoPlace byName = lookUp(key, city, null);
                place = byName.isFound() ? byName : place;
            }
            places.save(place);
            return toPlace(place);
        } catch (IOException e) {
            log.warn("Geocoding unavailable: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private GeoPlace lookUp(String key, String city, String postalCode) throws IOException, InterruptedException {
        StringBuilder query = new StringBuilder(properties.geocodingUrl())
            .append("/communes?fields=nom,centre,codeDepartement,codeRegion&boost=population&limit=1");
        if (city != null && !city.isBlank()) {
            query.append("&nom=").append(URLEncoder.encode(city.strip(), StandardCharsets.UTF_8));
        }
        if (postalCode != null) {
            query.append("&codePostal=").append(postalCode);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(query.toString()))
            .timeout(TIMEOUT)
            .header("User-Agent", properties.userAgent())
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("geocoding answered " + response.statusCode());
        }
        GeoPlace place = new GeoPlace();
        place.setQueryKey(key);
        place.setResolvedAt(clock.instant());
        JsonNode commune = Json.parse(response.body()).path(0);
        JsonNode coordinates = commune.path("centre").path("coordinates");
        Double longitude = Json.decimal(coordinates.path(0));
        Double latitude = Json.decimal(coordinates.path(1));
        if (latitude != null && longitude != null) {
            place.setFound(true);
            place.setCity(Json.text(commune.path("nom")));
            place.setDepartmentCode(Json.text(commune.path("codeDepartement")));
            place.setRegionCode(Json.text(commune.path("codeRegion")));
            place.setLatitude(latitude);
            place.setLongitude(longitude);
        }
        return place;
    }

    private static Optional<Place> toPlace(GeoPlace place) {
        if (!place.isFound() || place.getLatitude() == null || place.getLongitude() == null) {
            return Optional.empty();
        }
        return Optional.of(new Place(place.getCity(), place.getDepartmentCode(), place.getRegionCode(),
            place.getLatitude(), place.getLongitude()));
    }
}
