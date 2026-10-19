package dev.swirlit.indezy.scraping;

import com.sun.net.httpserver.HttpServer;
import dev.swirlit.indezy.model.GeoPlace;
import dev.swirlit.indezy.repository.GeoPlaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeoLocatorTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private HttpServer server;
    private final List<String> queries = new ArrayList<>();
    private volatile int status = 200;
    /** The answer to queries with a postal code; {@code null} answers like the others. */
    private volatile String answerWithPostalCode;
    private volatile String answer = "[{\"nom\":\"Courbevoie\",\"centre\":{\"type\":\"Point\",\"coordinates\":[2.2574,48.8976]}," +
        "\"codeDepartement\":\"92\",\"codeRegion\":\"11\"}]";
    private final GeoPlaceRepository places = mock(GeoPlaceRepository.class);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/communes", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            queries.add(query);
            String reply = answerWithPostalCode != null && query.contains("codePostal") ? answerWithPostalCode : answer;
            byte[] body = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        when(places.findById(any())).thenReturn(Optional.empty());
        when(places.save(any(GeoPlace.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void looksUpACityOnceAndCachesIt() {
        Optional<GeoLocator.Place> place = locator(true).locate("Courbevoie", "92400");

        assertThat(place).contains(new GeoLocator.Place("Courbevoie", "92", "11", 48.8976, 2.2574));
        assertThat(queries).singleElement().asString()
            .contains("fields=nom,centre,codeDepartement,codeRegion").contains("nom=Courbevoie").contains("codePostal=92400");
        ArgumentCaptor<GeoPlace> saved = ArgumentCaptor.forClass(GeoPlace.class);
        verify(places).save(saved.capture());
        assertThat(saved.getValue().getQueryKey()).isEqualTo("92400|courbevoie");
        assertThat(saved.getValue().isFound()).isTrue();
        assertThat(saved.getValue().getResolvedAt()).isEqualTo(NOW);
    }

    @Test
    void looksUpTheNameAloneWhenThePostalCodeMatchesNoCommune() {
        answerWithPostalCode = "[]";

        Optional<GeoLocator.Place> place = locator(true).locate("Paris", "75000");

        assertThat(place).get().extracting(GeoLocator.Place::city).isEqualTo("Courbevoie");
        assertThat(queries).hasSize(2);
        assertThat(queries.get(1)).doesNotContain("codePostal");
        ArgumentCaptor<GeoPlace> saved = ArgumentCaptor.forClass(GeoPlace.class);
        verify(places).save(saved.capture());
        assertThat(saved.getValue().getQueryKey()).isEqualTo("75000|paris");
        assertThat(saved.getValue().isFound()).isTrue();
    }

    @Test
    void answersFromTheCache() {
        GeoPlace cached = new GeoPlace();
        cached.setQueryKey("|paris");
        cached.setFound(true);
        cached.setCity("Paris");
        cached.setDepartmentCode("75");
        cached.setRegionCode("11");
        cached.setLatitude(48.8589);
        cached.setLongitude(2.347);
        when(places.findById("|paris")).thenReturn(Optional.of(cached));

        assertThat(locator(true).locate("Paris", "not a code")).get().extracting(GeoLocator.Place::departmentCode).isEqualTo("75");
        assertThat(queries).isEmpty();
    }

    @Test
    void remembersUnknownPlaces() {
        answer = "[]";

        assertThat(locator(true).locate("Atlantis", null)).isEmpty();

        ArgumentCaptor<GeoPlace> saved = ArgumentCaptor.forClass(GeoPlace.class);
        verify(places).save(saved.capture());
        assertThat(saved.getValue().isFound()).isFalse();

        GeoPlace unknown = new GeoPlace();
        unknown.setFound(false);
        when(places.findById("|atlantis")).thenReturn(Optional.of(unknown));
        assertThat(locator(true).locate("Atlantis", null)).isEmpty();
        assertThat(queries).hasSize(1);
    }

    @Test
    void staysQuietWhenDisabledUnavailableOrAskedNothing() {
        assertThat(locator(false).locate("Lyon", null)).isEmpty();
        assertThat(locator(true).locate(" ", null)).isEmpty();
        assertThat(queries).isEmpty();

        status = 503;
        assertThat(locator(true).locate("Lyon", "69002")).isEmpty();
        verify(places, never()).save(any());
    }

    private GeoLocator locator(boolean enabled) {
        OfferProperties properties = new OfferProperties(true, Duration.ofMinutes(5), Duration.ofSeconds(2), 10,
            Duration.ofHours(1), 90, "IndezyBot/test", enabled, "http://127.0.0.1:" + server.getAddress().getPort());
        return new GeoLocator(places, properties, HttpClient.newHttpClient(), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
