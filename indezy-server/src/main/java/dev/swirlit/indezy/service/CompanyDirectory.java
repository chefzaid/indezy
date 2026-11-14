package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.CompanyDto;
import dev.swirlit.indezy.scraping.Json;
import dev.swirlit.indezy.scraping.OfferProperties;
import dev.swirlit.indezy.util.ExpiringCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Looks companies up by name or SIREN in the French public directory, {@code recherche-entreprises.api.gouv.fr}, free and
 * keyless (see docs/features.md#company-lookup). Only the words typed are sent; answers are kept a day.
 */
@Service
@Slf4j
public class CompanyDirectory {

    static final int MIN_QUERY = 3;
    static final int MAX_QUERY = 100;
    static final int RESULTS = 5;
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final int SIREN_LENGTH = 9;
    private static final int VAT_MODULUS = 97;
    private static final int VAT_OFFSET = 12;
    private static final int VAT_FACTOR = 3;
    /** The most frequent legal forms (INSEE's nature juridique codes), the others shown by their code. */
    private static final Map<String, String> LEGAL_FORMS = Map.of("1000", "EI", "5498", "EURL", "5499", "SARL", "5710", "SAS",
        "5720", "SASU", "5599", "SA", "5505", "SA", "6540", "SCI", "9220", "Association");

    private final HttpClient http;
    private final Clock clock;
    private final String userAgent;
    private final ExpiringCache<String, List<CompanyDto>> answers = new ExpiringCache<>(Duration.ofDays(1), 1_000);

    @Value("${indezy.companies.url:https://recherche-entreprises.api.gouv.fr}")
    private String url;

    @Autowired
    public CompanyDirectory(OfferProperties properties) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), Clock.systemUTC(), properties.userAgent());
    }

    CompanyDirectory(HttpClient http, Clock clock, String userAgent) {
        this.http = http;
        this.clock = clock;
        this.userAgent = userAgent;
    }

    /** Up to {@value #RESULTS} companies matching a name or a SIREN; none for a query too short or when the directory fails. */
    public List<CompanyDto> search(String query) {
        String words = query == null ? "" : query.strip().replaceAll("\\s+", " ");
        if (words.length() < MIN_QUERY || words.length() > MAX_QUERY) {
            return List.of();
        }
        return answers.get(words.toLowerCase(Locale.ROOT), clock.instant(), key -> lookUp(words));
    }

    private List<CompanyDto> lookUp(String words) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/search?per_page=" + RESULTS + "&q=" +
                    URLEncoder.encode(words, StandardCharsets.UTF_8)))
                .timeout(TIMEOUT)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException("directory answered " + response.statusCode());
            }
            List<CompanyDto> companies = new ArrayList<>();
            for (JsonNode result : Json.parse(response.body()).path("results")) {
                companies.add(company(result));
            }
            return List.copyOf(companies);
        } catch (IOException e) {
            log.warn("Company lookup failed: {}", e.getClass().getSimpleName());
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private static CompanyDto company(JsonNode result) {
        JsonNode office = result.path("siege");
        String siren = Json.text(result.path("siren"));
        String name = Json.text(result.path("nom_raison_sociale"));
        String postalCode = Json.text(office.path("code_postal"));
        String city = Json.text(office.path("libelle_commune"));
        String natureCode = Json.text(result.path("nature_juridique"));
        return new CompanyDto(siren, name != null ? name : Json.text(result.path("nom_complet")),
            street(Json.text(office.path("adresse")), postalCode, city), postalCode, city,
            Json.text(result.path("activite_principale")), natureCode == null ? null : LEGAL_FORMS.getOrDefault(natureCode, natureCode),
            vatNumber(siren), "C".equals(Json.text(result.path("etat_administratif"))));
    }

    /**
     * Who the registry names for an establishment: whether its legal unit and the establishment itself are active, and
     * the people it names (the entrepreneur of an EI, the executives of a company), each "first names last name".
     */
    public record Registration(String siren, boolean active, boolean establishmentActive, List<String> people) {

        public Registration {
            people = List.copyOf(people);
        }
    }

    /** The registry is down or answered an error: nothing can be said of the SIRET. */
    public static final class UnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UnavailableException(Throwable cause) {
            super("The company registry could not be reached", cause);
        }
    }

    /**
     * The registration of a SIRET, read afresh (a verification must not rest on a day-old answer); empty when the registry
     * knows no such establishment, and an {@link UnavailableException} when it cannot be asked.
     */
    public Optional<Registration> registration(String siret) {
        if (siret == null || !siret.matches("\\d{14}")) {
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/search?per_page=1&q=" + siret))
                .timeout(TIMEOUT).header("User-Agent", userAgent).header("Accept", "application/json").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException("directory answered " + response.statusCode());
            }
            String siren = siret.substring(0, SIREN_LENGTH);
            for (JsonNode result : Json.parse(response.body()).path("results")) {
                if (siren.equals(Json.text(result.path("siren")))) {
                    return Optional.of(registration(siret, result));
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            log.warn("Registry lookup failed: {}", e.getClass().getSimpleName());
            throw new UnavailableException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnavailableException(e);
        }
    }

    private static Registration registration(String siret, JsonNode result) {
        boolean active = "A".equals(Json.text(result.path("etat_administratif")));
        boolean establishment = active;
        for (JsonNode office : result.path("matching_etablissements")) {
            if (siret.equals(Json.text(office.path("siret")))) {
                establishment = "A".equals(Json.text(office.path("etat_administratif")));
            }
        }
        List<String> people = new ArrayList<>();
        for (JsonNode person : result.path("dirigeants")) {
            String last = Json.text(person.path("nom"));
            if (last != null && !"personne morale".equals(Json.text(person.path("type_dirigeant")))) {
                String first = Json.text(person.path("prenoms"));
                people.add(first == null ? last : first + " " + last);
            }
        }
        if ("1000".equals(Json.text(result.path("nature_juridique"))) && Json.text(result.path("nom_complet")) != null) {
            people.add(Json.text(result.path("nom_complet")));
        }
        return new Registration(Json.text(result.path("siren")), active, establishment, people);
    }

    /** The head office's street, without the postal code and town the directory appends. */
    static String street(String address, String postalCode, String city) {
        if (address == null) {
            return null;
        }
        String tail = (postalCode == null ? "" : postalCode) + (city == null ? "" : " " + city);
        String street = address.strip();
        if (!tail.isBlank() && street.toUpperCase(Locale.ROOT).endsWith(tail.strip().toUpperCase(Locale.ROOT))) {
            street = street.substring(0, street.length() - tail.strip().length()).strip();
        }
        return street.isEmpty() ? null : street;
    }

    /** The French intra-EU VAT number of a SIREN: FR, a two-digit key, then the SIREN. */
    static String vatNumber(String siren) {
        if (siren == null || !siren.matches("\\d{" + SIREN_LENGTH + "}")) {
            return null;
        }
        long key = (VAT_OFFSET + VAT_FACTOR * (Long.parseLong(siren) % VAT_MODULUS)) % VAT_MODULUS;
        return String.format(Locale.ROOT, "FR%02d%s", key, siren);
    }
}
