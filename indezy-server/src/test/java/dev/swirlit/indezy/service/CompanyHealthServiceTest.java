package dev.swirlit.indezy.service;

import com.sun.net.httpserver.HttpServer;
import dev.swirlit.indezy.dto.CompanyDto;
import dev.swirlit.indezy.dto.CompanyHealthDto;
import dev.swirlit.indezy.dto.CompanyHealthDto.Procedure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyHealthServiceTest {

    private static final String SIREN = "123456789";

    private HttpServer server;
    private final List<String> wheres = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String answer = """
        {"total_count": 1, "results": [{"dateparution": "2026-05-03", "familleavis": "collective",
          "familleavis_lib": "Procédures collectives",
          "jugement": "{\\"type\\": \\"initial\\", \\"famille\\": \\"Jugement d'ouverture\\", \
        \\"nature\\": \\"Jugement d'ouverture d'une procédure de redressement judiciaire\\", \\"date\\": \\"2026-04-29\\"}"}]}""";
    private final CompanyDirectory directory = mock(CompanyDirectory.class);
    private CompanyHealthService service;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/records", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            wheres.add(URLDecoder.decode(query.substring(query.indexOf("where=") + "where=".length()), StandardCharsets.UTF_8));
            byte[] body = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        service = new CompanyHealthService(directory, HttpClient.newHttpClient(),
            Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC), "IndezyTest");
        ReflectionTestUtils.setField(service, "bodaccUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/records");
        when(directory.search(anyString())).thenReturn(List.of());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static CompanyDto company(boolean closed) {
        return new CompanyDto(SIREN, "ESN", null, null, "Paris", null, "SAS", "FR32123456789", closed);
    }

    @Test
    void reportsTheLatestProceedingsOfTheLastThreeYears() {
        when(directory.search(SIREN)).thenReturn(List.of(company(false)));

        assertThat(service.check("123 456 789"))
            .isEqualTo(new CompanyHealthDto(true, false, Procedure.REDRESSEMENT, LocalDate.of(2026, 5, 3)));
        assertThat(wheres).containsExactly("\"123456789\" and familleavis=\"collective\" and dateparution>=date'2023-10-31'");
        service.check(SIREN);
        assertThat(wheres).hasSize(1);
    }

    @Test
    void reportsAClosedCompanyWithoutProceedings() {
        answer = "{\"total_count\": 0, \"results\": []}";
        when(directory.search(SIREN)).thenReturn(List.of(new CompanyDto("999999999", "Other", null, null, null, null, null, null, false),
            company(true)));
        assertThat(service.check(SIREN)).isEqualTo(new CompanyHealthDto(true, true, null, null));
    }

    @Test
    void staysUncheckedWithoutASirenOrAnAnswer() {
        assertThat(service.check(null)).isEqualTo(CompanyHealthDto.UNCHECKED);
        assertThat(service.check("12345")).isEqualTo(CompanyHealthDto.UNCHECKED);
        status = 500;
        assertThat(service.check(SIREN)).isEqualTo(CompanyHealthDto.UNCHECKED);
        assertThat(wheres).hasSize(1);
    }

    @Test
    void readsTheFamilyWhenTheJudgementIsMissing() {
        answer = "{\"results\": [{\"dateparution\": \"not a date\", \"familleavis_lib\": \"Procédures collectives\"}]}";
        assertThat(service.check(SIREN)).isEqualTo(new CompanyHealthDto(true, false, Procedure.OTHER, null));
    }

    @Test
    void namesTheGravestProceedings() {
        assertThat(CompanyHealthService.procedure("Jugement prononçant la liquidation judiciaire")).isEqualTo(Procedure.LIQUIDATION);
        assertThat(CompanyHealthService.procedure("Jugement de conversion en liquidation judiciaire après redressement"))
            .isEqualTo(Procedure.LIQUIDATION);
        assertThat(CompanyHealthService.procedure("Jugement d'ouverture d'une procédure de sauvegarde")).isEqualTo(Procedure.SAUVEGARDE);
        assertThat(CompanyHealthService.procedure("Jugement arrêtant le plan de cession")).isEqualTo(Procedure.OTHER);
        assertThat(CompanyHealthService.procedure(null)).isEqualTo(Procedure.OTHER);
    }
}
