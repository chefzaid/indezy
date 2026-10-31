package dev.swirlit.indezy.service;

import com.sun.net.httpserver.HttpServer;
import dev.swirlit.indezy.dto.CompanyDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyDirectoryTest {

    private HttpServer server;
    private final List<String> queries = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String answer = """
        {"results": [
          {"siren": "542051180", "nom_complet": "TOTALENERGIES SE (TOTALENERGIES)", "nom_raison_sociale": "TOTALENERGIES SE",
           "nature_juridique": "5599", "activite_principale": "70.10Z", "etat_administratif": "A",
           "siege": {"adresse": "2 PLACE JEAN MILLIER 92400 COURBEVOIE", "code_postal": "92400", "libelle_commune": "COURBEVOIE"}},
          {"siren": "123456789", "nom_complet": "ANCIENNE ESN", "nature_juridique": "5785", "etat_administratif": "C",
           "siege": {"adresse": "1 RUE DU PORT", "libelle_commune": "LYON"}}
        ], "total_results": 2}""";
    private CompanyDirectory directory;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            byte[] body = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        directory = new CompanyDirectory(HttpClient.newHttpClient(), Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC),
            "IndezyTest");
        ReflectionTestUtils.setField(directory, "url", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void findsCompaniesWithTheirHeadOfficeLegalFormAndVatNumber() {
        List<CompanyDto> found = directory.search("  total   energies ");

        assertThat(found).containsExactly(
            new CompanyDto("542051180", "TOTALENERGIES SE", "2 PLACE JEAN MILLIER", "92400", "COURBEVOIE", "70.10Z", "SA",
                "FR59542051180", false),
            new CompanyDto("123456789", "ANCIENNE ESN", "1 RUE DU PORT", null, "LYON", null, "5785", "FR32123456789", true));
        assertThat(queries).containsExactly("per_page=5&q=total+energies");
        // Kept a day: the same words, whatever their case, are not asked again.
        directory.search("Total Energies");
        assertThat(queries).hasSize(1);
    }

    @Test
    void asksNothingForAQueryTooShortOrTooLong() {
        assertThat(directory.search("ab")).isEmpty();
        assertThat(directory.search(null)).isEmpty();
        assertThat(directory.search("x".repeat(101))).isEmpty();
        assertThat(queries).isEmpty();
    }

    @Test
    void findsNothingWhenTheDirectoryFails() {
        status = 503;
        assertThat(directory.search("acme")).isEmpty();
        status = 200;
        answer = "not json";
        assertThat(directory.search("other")).isEmpty();
    }

    @Test
    void computesTheVatNumberAndCutsTheStreet() {
        assertThat(CompanyDirectory.vatNumber("542051180")).isEqualTo("FR59542051180");
        assertThat(CompanyDirectory.vatNumber("12345")).isNull();
        assertThat(CompanyDirectory.vatNumber(null)).isNull();
        assertThat(CompanyDirectory.street(null, "75001", "PARIS")).isNull();
        assertThat(CompanyDirectory.street("75001 PARIS", "75001", "PARIS")).isNull();
        assertThat(CompanyDirectory.street("3 rue X", null, null)).isEqualTo("3 rue X");
    }
}
