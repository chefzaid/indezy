package dev.swirlit.indezy.service;

import dev.swirlit.indezy.scraping.Json;
import dev.swirlit.indezy.scraping.OfferProperties;
import dev.swirlit.indezy.util.ExpiringCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * CPF-eligible trainings for a skill (see docs/features.md#skills-gap), read from the Mon Compte Formation catalogue
 * the Caisse des Dépôts publishes as open data ({@code opendata.caissedesdepots.fr}, dataset
 * {@code moncompteformation_catalogueformation}, Licence Ouverte, refreshed daily): the trainings whose title names the
 * skill, each once whatever the regions it is given in, those given remotely first, then those with the most sessions.
 * Only the skill is sent; answers are kept a day.
 */
@Service
@Slf4j
public class TrainingCatalog {

    static final int RESULTS = 5;
    static final int SKILL_MAX = 60;
    private static final int READ = 30;
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final String DATASET = "/api/explore/v2.1/catalog/datasets/moncompteformation_catalogueformation/records";
    private static final String FIELDS = "numero_formation,intitule_formation,nom_of,intitule_certification,nombre_heures_total_mean," +
        "frais_ttc_tot_mean,nb_session_a_distance,nb_session_active,nom_region";

    private final HttpClient http;
    private final Clock clock;
    private final String userAgent;
    private final ExpiringCache<String, List<Training>> answers = new ExpiringCache<>(Duration.ofDays(1), 500);

    @Value("${indezy.trainings.catalog-url:https://opendata.caissedesdepots.fr}")
    private String url;

    /** A training: its title, its provider, the certification it leads to, its usual hours and price, whether it is given remotely, its regions. */
    public record Training(String id, String title, String provider, String certification, Integer hours, Integer price, boolean remote,
                           List<String> regions) {

        public Training {
            regions = List.copyOf(regions);
        }
    }

    @Autowired
    public TrainingCatalog(OfferProperties properties) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), Clock.systemUTC(), properties.userAgent());
    }

    TrainingCatalog(HttpClient http, Clock clock, String userAgent) {
        this.http = http;
        this.clock = clock;
        this.userAgent = userAgent;
    }

    /** Up to {@value #RESULTS} trainings for {@code skill}; 503 when the catalogue cannot be read. */
    public List<Training> forSkill(String skill) {
        String words = skill == null ? "" : skill.strip().replaceAll("[\"\\\\]", "").replaceAll("\\s+", " ");
        if (words.isEmpty() || words.length() > SKILL_MAX) {
            return List.of();
        }
        return answers.get(words.toLowerCase(Locale.ROOT), clock.instant(), this::lookUp);
    }

    private List<Training> lookUp(String words) {
        try {
            String query = "where=" + encode("search(intitule_formation, \"" + words + "\")") + "&order_by=" +
                encode("nb_session_a_distance desc, nb_session_active desc") + "&limit=" + READ + "&select=" + encode(FIELDS);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + DATASET + "?" + query))
                .timeout(TIMEOUT)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException("catalogue answered " + response.statusCode());
            }
            return merge(Json.parse(response.body()).path("results"));
        } catch (IOException e) {
            log.warn("Training catalogue read failed: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The training catalogue could not be read");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The training catalogue could not be read");
        }
    }

    /** Each training once (it has a row per region it is given in), remote ones first, then by sessions, {@value #RESULTS} at most. */
    static List<Training> merge(JsonNode results) {
        Map<String, JsonNode> first = new LinkedHashMap<>();
        Map<String, TreeSet<String>> regions = new LinkedHashMap<>();
        Map<String, Boolean> remote = new LinkedHashMap<>();
        Map<String, Integer> sessions = new LinkedHashMap<>();
        for (JsonNode row : results) {
            String id = Objects.requireNonNullElse(Json.text(row.path("numero_formation")), Json.text(row.path("intitule_formation")));
            if (id == null) {
                continue;
            }
            first.putIfAbsent(id, row);
            String region = Json.text(row.path("nom_region"));
            regions.computeIfAbsent(id, key -> new TreeSet<>());
            if (region != null) {
                regions.get(id).add(region);
            }
            remote.merge(id, Objects.requireNonNullElse(Json.integer(row.path("nb_session_a_distance")), 0) > 0, Boolean::logicalOr);
            sessions.merge(id, Objects.requireNonNullElse(Json.integer(row.path("nb_session_active")), 0), Integer::sum);
        }
        List<Training> trainings = new ArrayList<>();
        first.forEach((id, row) -> trainings.add(new Training(id, Json.text(row.path("intitule_formation")), Json.text(row.path("nom_of")),
            Json.text(row.path("intitule_certification")), positive(Json.decimal(row.path("nombre_heures_total_mean"))),
            positive(Json.decimal(row.path("frais_ttc_tot_mean"))), remote.get(id), List.copyOf(regions.get(id)))));
        return trainings.stream()
            .sorted(Comparator.comparing(Training::remote).reversed().thenComparing(training -> -sessions.get(training.id())))
            .limit(RESULTS).toList();
    }

    private static Integer positive(Double value) {
        return value == null || value <= 0 ? null : (int) Math.round(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
