package dev.swirlit.indezy.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrainingCatalogTest {

    private HttpServer server;
    private final List<String> queries = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    // The catalogue's rows as it answered on 2026-11-24: one row per region a training is given in.
    private final String answer = """
        {"total_count": 4, "results": [
          {"numero_formation": "81227101300045_KafkaRNCP", "intitule_formation": "Kafka", "nom_of": "AMBIENT IT",
           "intitule_certification": "TP Concepteur développeur d'applications", "nombre_heures_total_mean": 0,
           "frais_ttc_tot_mean": 2400.0, "nb_session_a_distance": 10, "nb_session_active": 10, "nom_region": null},
          {"numero_formation": "81227101300045_KafkaRNCP", "intitule_formation": "Kafka", "nom_of": "AMBIENT IT",
           "intitule_certification": "TP Concepteur développeur d'applications", "nombre_heures_total_mean": 0,
           "frais_ttc_tot_mean": 2400.0, "nb_session_a_distance": 0, "nb_session_active": 10, "nom_region": "Île-de-France"},
          {"numero_formation": "1_KafkaLyon", "intitule_formation": "Apache Kafka", "nom_of": "FORMA LYON", "intitule_certification": null,
           "nombre_heures_total_mean": 21.4, "frais_ttc_tot_mean": 1800.0, "nb_session_a_distance": 0, "nb_session_active": 30,
           "nom_region": "Auvergne-Rhône-Alpes"},
          {"numero_formation": "2_KafkaRemote", "intitule_formation": "Kafka à distance", "nom_of": "WEB TRAINING",
           "nombre_heures_total_mean": 14, "frais_ttc_tot_mean": 990.0, "nb_session_a_distance": 2, "nb_session_active": 2,
           "nom_region": "Bretagne"}
        ]}""";
    private TrainingCatalog catalog;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/explore/v2.1/catalog/datasets/moncompteformation_catalogueformation/records", exchange -> {
            queries.add(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            byte[] body = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        catalog = new TrainingCatalog(HttpClient.newHttpClient(), Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC), "IndezyTest");
        ReflectionTestUtils.setField(catalog, "url", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void listsEachTrainingOnceTheRemoteOnesFirstWithItsRegions() {
        List<TrainingCatalog.Training> trainings = catalog.forSkill("  Kafka ");

        assertThat(trainings).extracting(TrainingCatalog.Training::id).containsExactly("81227101300045_KafkaRNCP", "2_KafkaRemote", "1_KafkaLyon");
        TrainingCatalog.Training first = trainings.getFirst();
        assertThat(first.remote()).isTrue();
        assertThat(first.regions()).containsExactly("Île-de-France");
        assertThat(first.hours()).isNull();
        assertThat(first.price()).isEqualTo(2400);
        assertThat(trainings.get(2).hours()).isEqualTo(21);
        assertThat(queries).singleElement().asString().contains("where=search(intitule_formation, \"kafka\")", "nb_session_a_distance desc");
        // Kept a day, whatever the case.
        catalog.forSkill("kafka");
        assertThat(queries).hasSize(1);
    }

    @Test
    void asksNothingForNoSkillAndSaysWhenTheCatalogueCannotBeRead() {
        assertThat(catalog.forSkill(" ")).isEmpty();
        assertThat(catalog.forSkill("x".repeat(61))).isEmpty();
        assertThat(queries).isEmpty();
        status = 500;
        assertThatThrownBy(() -> catalog.forSkill("Terraform")).isInstanceOf(ResponseStatusException.class);
        // A quote cannot end the search early.
        status = 200;
        catalog.forSkill("C\"++");
        assertThat(queries.getLast()).contains("\"c++\"");
    }
}
