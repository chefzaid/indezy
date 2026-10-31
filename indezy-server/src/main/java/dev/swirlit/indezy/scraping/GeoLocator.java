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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
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
    /** Addresses are cached beside the cities, told apart by this prefix. */
    private static final String ADDRESS_KEY = "address|";
    private static final int MAX_KEY = 200;
    /** Below this confidence the search guessed (a street of another town, a lone city): the address is left unknown. */
    static final double MIN_ADDRESS_SCORE = 0.4;

    private final GeoPlaceRepository places;
    private final OfferProperties properties;
    private final HttpClient http;
    private final Clock clock;
    private final OfferMetrics metrics;

    @Autowired
    public GeoLocator(GeoPlaceRepository places, OfferProperties properties, OfferMetrics metrics) {
        this(places, properties, HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), Clock.systemUTC(), metrics);
    }

    GeoLocator(GeoPlaceRepository places, OfferProperties properties, HttpClient http, Clock clock, OfferMetrics metrics) {
        this.places = places;
        this.properties = properties;
        this.http = http;
        this.clock = clock;
        this.metrics = metrics;
    }

    /** The commune named {@code city} (in {@code postalCode} when known); empty when unknown or not looked up. */
    public Optional<Place> locate(String city, String postalCode) {
        String name = Objects.requireNonNullElse(TextFolding.words(city), "");
        String code = postalCode != null && postalCode.trim().matches("\\d{5}") ? postalCode.trim() : null;
        if (name.isEmpty() && code == null) {
            return Optional.empty();
        }
        String key = (code == null ? "" : code) + "|" + name;
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
            if (!place.isFound() && code != null && !name.isEmpty()) {
                GeoPlace byName = lookUp(key, city, null);
                place = byName.isFound() ? byName : place;
            }
            places.save(place);
            return toPlace(place);
        } catch (IOException e) {
            log.warn("Geocoding unavailable: {}", e.getMessage());
            metrics.geocodingTimedOut();
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

    /** Whether places are looked up at all on this server. */
    public boolean canLocate() {
        return properties.geocodingEnabled();
    }

    /**
     * The street address {@code address} (number, street, postal code and city, in any order), from the national
     * address search; empty when unknown, too uncertain or not looked up. Cached like the cities.
     */
    public Optional<Place> locateAddress(String address) {
        String words = Objects.requireNonNullElse(TextFolding.words(address), "");
        if (words.isEmpty()) {
            return Optional.empty();
        }
        String key = ADDRESS_KEY + (words.length() + ADDRESS_KEY.length() <= MAX_KEY ? words : sha256(words));
        Optional<GeoPlace> cached = places.findById(key);
        if (cached.isPresent()) {
            return toPlace(cached.get());
        }
        if (!properties.geocodingEnabled()) {
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.addressUrl() + "/search?limit=1&q=" +
                    URLEncoder.encode(address.strip(), StandardCharsets.UTF_8)))
                .timeout(TIMEOUT)
                .header("User-Agent", properties.userAgent())
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException("address search answered " + response.statusCode());
            }
            GeoPlace place = new GeoPlace();
            place.setQueryKey(key);
            place.setResolvedAt(clock.instant());
            JsonNode feature = Json.parse(response.body()).path("features").path(0);
            JsonNode coordinates = feature.path("geometry").path("coordinates");
            Double longitude = Json.decimal(coordinates.path(0));
            Double latitude = Json.decimal(coordinates.path(1));
            Double score = Json.decimal(feature.path("properties").path("score"));
            if (latitude != null && longitude != null && score != null && score >= MIN_ADDRESS_SCORE) {
                place.setFound(true);
                place.setCity(Json.text(feature.path("properties").path("city")));
                String context = Json.text(feature.path("properties").path("context"));
                place.setDepartmentCode(context == null ? null : context.split(",")[0].strip());
                place.setLatitude(latitude);
                place.setLongitude(longitude);
            }
            places.save(place);
            return toPlace(place);
        } catch (IOException e) {
            // The address is personal data: only the failure is logged.
            log.warn("Address search unavailable: {}", e.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Optional<Place> toPlace(GeoPlace place) {
        if (!place.isFound() || place.getLatitude() == null || place.getLongitude() == null) {
            return Optional.empty();
        }
        return Optional.of(new Place(place.getCity(), place.getDepartmentCode(), place.getRegionCode(),
            place.getLatitude(), place.getLongitude()));
    }
}
