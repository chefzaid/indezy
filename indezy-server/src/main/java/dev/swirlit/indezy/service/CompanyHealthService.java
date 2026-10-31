package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.CompanyDto;
import dev.swirlit.indezy.dto.CompanyHealthDto;
import dev.swirlit.indezy.dto.CompanyHealthDto.Procedure;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Checks that a client or an ESN is still in business (see docs/features.md#company-health-check): closed according to
 * the public directory ({@link CompanyDirectory}), or under collective proceedings (sauvegarde, redressement,
 * liquidation) according to its latest BODACC announcement of the last {@value #PROCEDURE_YEARS} years, from the
 * DILA's open data, keyless. Only the SIREN is sent; answers are kept a day.
 */
@Service
@Slf4j
public class CompanyHealthService {

    static final int PROCEDURE_YEARS = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final String BODACC_RECORDS =
        "https://bodacc-datadila.opendatasoft.com/api/explore/v2.1/catalog/datasets/annonces-commerciales/records";

    private final CompanyDirectory directory;
    private final HttpClient http;
    private final Clock clock;
    private final String userAgent;
    private final ExpiringCache<String, CompanyHealthDto> answers = new ExpiringCache<>(Duration.ofDays(1), 2_000);

    @Value("${indezy.companies.bodacc-url:" + BODACC_RECORDS + "}")
    private String bodaccUrl;

    @Autowired
    public CompanyHealthService(CompanyDirectory directory, OfferProperties properties) {
        this(directory, HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), Clock.systemUTC(), properties.userAgent());
    }

    CompanyHealthService(CompanyDirectory directory, HttpClient http, Clock clock, String userAgent) {
        this.directory = directory;
        this.http = http;
        this.clock = clock;
        this.userAgent = userAgent;
    }

    /** The company's health; unchecked without a valid SIREN. */
    public CompanyHealthDto check(String siren) {
        String digits = siren == null ? "" : siren.replaceAll("\\s", "");
        if (!digits.matches("\\d{9}")) {
            return CompanyHealthDto.UNCHECKED;
        }
        return answers.get(digits, clock.instant(), this::lookUp);
    }

    private CompanyHealthDto lookUp(String siren) {
        Optional<CompanyDto> company = directory.search(siren).stream().filter(found -> siren.equals(found.siren())).findFirst();
        Optional<Announcement> procedure = latestProcedure(siren);
        if (company.isEmpty() && procedure.isEmpty()) {
            return CompanyHealthDto.UNCHECKED;
        }
        return new CompanyHealthDto(true, company.map(CompanyDto::closed).orElse(false),
            procedure.map(Announcement::procedure).orElse(null), procedure.map(Announcement::on).orElse(null));
    }

    private record Announcement(Procedure procedure, LocalDate on) {
    }

    /** The latest collective proceedings announcement of the last years; empty when none, or BODACC did not answer. */
    private Optional<Announcement> latestProcedure(String siren) {
        LocalDate since = LocalDate.now(clock.withZone(PARIS)).minusYears(PROCEDURE_YEARS);
        String where = "\"" + siren + "\" and familleavis=\"collective\" and dateparution>=date'" + since + "'";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(bodaccUrl + "?order_by=dateparution%20desc&limit=1&where=" +
                    URLEncoder.encode(where, StandardCharsets.UTF_8).replace("+", "%20")))
                .timeout(TIMEOUT)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException("BODACC answered " + response.statusCode());
            }
            JsonNode record = Json.parse(response.body()).path("results").path(0);
            if (record.isMissingNode()) {
                return Optional.empty();
            }
            String judgement = Json.text(record.path("jugement"));
            String nature = judgement == null ? null : Json.text(Json.parse(judgement).path("nature"));
            String text = (nature != null ? nature : Json.text(record.path("familleavis_lib")));
            return Optional.of(new Announcement(procedure(text), date(Json.text(record.path("dateparution")))));
        } catch (IOException e) {
            log.warn("BODACC lookup failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** The proceedings a judgement's wording names, the gravest first. */
    static Procedure procedure(String wording) {
        String text = wording == null ? "" : wording.toLowerCase(Locale.ROOT);
        for (Procedure procedure : List.of(Procedure.LIQUIDATION, Procedure.REDRESSEMENT, Procedure.SAUVEGARDE)) {
            if (text.contains(procedure.name().toLowerCase(Locale.ROOT))) {
                return procedure;
            }
        }
        return Procedure.OTHER;
    }

    private static LocalDate date(String value) {
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
