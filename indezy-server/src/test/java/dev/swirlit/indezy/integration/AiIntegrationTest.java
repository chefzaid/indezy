package dev.swirlit.indezy.integration;

import com.sun.net.httpserver.HttpServer;
import dev.swirlit.indezy.ai.OfferTriageAiService;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.JobOfferRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AI features over HTTP against a stand-in for Anthropic's API: the request the SDK sends, the answer read into an
 * opportunity's fields or a message, the usage counted against the account's allowance, the member's own key, a refusal
 * and a refused key.
 */
class AiIntegrationTest extends TwoAccountsIntegrationTest {

    private static final HttpServer ANTHROPIC;
    private static final List<String> BODIES = new CopyOnWriteArrayList<>();
    private static final List<String> KEYS = new CopyOnWriteArrayList<>();
    private static final List<String> BETAS = new CopyOnWriteArrayList<>();

    static {
        try {
            ANTHROPIC = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        ANTHROPIC.createContext("/v1/messages", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            // The SDK sends the key as a bearer token, older ones as x-api-key.
            String bearer = exchange.getRequestHeaders().getFirst("Authorization");
            String key = bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7) : exchange.getRequestHeaders().getFirst("x-api-key");
            BODIES.add(body);
            KEYS.add(key);
            BETAS.add(String.valueOf(exchange.getRequestHeaders().getFirst("anthropic-beta")));
            int status = 200;
            String answer;
            if ("sk-ant-refused".equals(key)) {
                status = 401;
                answer = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
            } else if (body.contains("PLEASE-REFUSE")) {
                answer = message("", "refusal");
            } else if (body.contains("You prepare a French freelance")) {
                answer = message("{\"counterOffer\":680,\"walkAway\":720,\"strategy\":\"Ancrer haut.\",\"arguments\":[\"Kafka rare\",\"\"]}",
                    "end_turn");
            } else if (body.contains("You write the posts")) {
                answer = message("{\"linkedin\":\"Disponible bientôt pour une mission Java.\",\"network\":\" Dispo en janvier. \"}", "end_turn");
            } else if (body.contains("You polish")) {
                answer = message("{\"subject\":\"Point sur la mission\",\"body\":\"Bonjour {{firstName}},\\n\\nOù en est-on ?\"}", "end_turn");
            } else if (body.contains("You suggest the daily rate")) {
                answer = message("{\"rate\":700,\"low\":650,\"high\":600,\"reasons\":[\"Kafka est rare\",\" \"]}", "end_turn");
            } else if (body.contains("You tailor")) {
                answer = message("{\"summary\":\"Développeur Java tourné vers les flux de paie.\",\"skills\":[\"Cobol\"]," +
                    "\"missions\":[{\"number\":1,\"description\":\"Migration de la paie vers Kafka, au cœur des flux.\"},{\"number\":9,\"description\":\"Inventée\"}]}",
                    "end_turn");
            } else if (body.contains("You triage")) {
                answer = message("{\"goodFit\":false,\"verdict\":\"Sur site tous les jours.\",\"dealBreakers\":[\"5 jours sur site\",\"\",\"TJM sous l'objectif\"]}",
                    "end_turn");
            } else if (body.contains("You are the coach")) {
                answer = message("Préparez trois exemples de projets Kafka.", "end_turn");
            } else if (body.contains("<profile>")) {
                answer = message("{\"score\":140,\"verdict\":\"Une bonne mission.\",\"pros\":[\"Stack Java\",\" \",\"Télétravail\"]," +
                    "\"cons\":[\"Loin\"],\"softCriteria\":[\"Le style du manager\"],\"questions\":[\"Taille de l'équipe ?\"]," +
                    "\"rateAdvice\":\"Le TJM est dans le marché.\",\"suggestedRate\":700}", "end_turn");
            } else if (body.contains("<posting>")) {
                answer = message("{\"role\":\"Développeur Java Senior\",\"company\":\"Conseil X\",\"companyIsIntermediary\":true,\"endClient\":\"\"," +
                    "\"dailyRate\":650,\"techStack\":[\"Java\",\" Spring Boot \",\"Java\",\"\"],\"place\":\"92400 Courbevoie\"," +
                    "\"workMode\":\"HYBRID\",\"remoteDaysPerWeek\":2,\"startDate\":\"2027-01-04\",\"durationInMonths\":0," +
                    "\"summary\":\"Refonte d'un SI de paiement.\"}", "end_turn");
            } else {
                answer = message("{\"subject\":\"Mission Java Senior\",\"body\":\"Bonjour,\\n\\nJe reviens vers vous.\\n\\nAlice\"}", "end_turn");
            }
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        ANTHROPIC.start();
    }

    private static String message(String json, String stopReason) {
        String text = json.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5-5\",\"content\":[" +
            (json.isEmpty() ? "" : "{\"type\":\"text\",\"text\":\"" + text + "\"}") + "],\"stop_reason\":\"" + stopReason +
            "\",\"stop_sequence\":null,\"usage\":{\"input_tokens\":1000,\"output_tokens\":200}}";
    }

    @Autowired
    private JobOfferRepository offers;

    @Autowired
    private OfferTriageAiService triageService;

    private static JobOffer offer() {
        JobOffer offer = new JobOffer();
        offer.setSite(OfferSite.FREE_WORK);
        offer.setExternalId("ai-" + UUID.randomUUID());
        offer.setUrl("https://www.free-work.com/fr/tech-it/job-mission/kotlin/" + UUID.randomUUID());
        offer.setTitle("Lead Kotlin");
        offer.setCompany("Conseil Z");
        offer.setDescription("Sur site 5 jours");
        offer.setRateMin(450);
        offer.setRateMax(500);
        offer.setWorkMode(WorkMode.ONSITE);
        offer.setSkills(List.of("Kotlin"));
        offer.setSearchText("lead kotlin");
        Instant now = Instant.now();
        offer.setPublishedAt(now);
        offer.setDatePrecision(OfferDatePrecision.EXACT);
        offer.setFirstSeenAt(now);
        offer.setLastSeenAt(now);
        return offer;
    }

    @DynamicPropertySource
    static void anthropic(DynamicPropertyRegistry registry) {
        registry.add("indezy.ai.api-key", () -> "sk-ant-indezy");
        // Every task on Opus, so the allowance below counts the same price for each request.
        registry.add("indezy.ai.model", () -> "claude-opus-5-5");
        registry.add("indezy.ai.base-url", () -> "http://127.0.0.1:" + ANTHROPIC.getAddress().getPort());
        // 1.5 cents a month: two requests of 0.8 cents go (the second starts at 0.8), the third is refused.
        registry.add("indezy.ai.account-monthly-cap-usd", () -> "0.015");
    }

    @AfterAll
    static void stop() {
        ANTHROPIC.stop(0);
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsPostingsAndDraftsMessagesWithinTheAllowanceOrWithTheMembersKey() throws IOException {
        assertThat(call(alice, HttpMethod.GET, "/ai/status", null, Map.class).getBody()).containsEntry("available", true)
            .containsEntry("ownKey", false).containsEntry("model", null).containsEntry("limitUsd", 0.015);
        assertThat((Map<String, Object>) call(alice, HttpMethod.GET, "/ai/status", null, Map.class).getBody().get("models"))
            .containsEntry("POSTING", "claude-opus-5-5").containsEntry("DOSSIER", "claude-opus-5-5");

        // A posting read into an opportunity's fields, through Indezy's key with the refusal fallback on.
        Map<String, Object> posting = call(alice, HttpMethod.POST, "/ai/posting", Map.of("text", "Mission Java Senior chez Conseil X..."),
            Map.class).getBody();
        assertThat(posting).containsEntry("role", "Développeur Java Senior").containsEntry("company", "Conseil X")
            .containsEntry("companyIsIntermediary", true).containsEntry("endClient", null).containsEntry("dailyRate", 650)
            .containsEntry("place", "92400 Courbevoie").containsEntry("workMode", "HYBRID").containsEntry("remoteDaysPerMonth", 8)
            .containsEntry("startDate", "2027-01-04").containsEntry("durationInMonths", null);
        assertThat((List<String>) posting.get("techStack")).containsExactly("Java", "Spring Boot");
        String sent = BODIES.getLast();
        assertThat(sent).contains("\"model\":\"claude-opus-5-5\"", "\"fallbacks\":\"default\"", "\"effort\":\"low\"", "json_schema",
            "Mission Java Senior chez Conseil X");
        assertThat(KEYS.getLast()).isEqualTo("sk-ant-indezy");
        assertThat(BETAS.getLast()).contains("server-side-fallback-2026-07-01");
        assertThat(((Number) call(alice, HttpMethod.GET, "/ai/status", null, Map.class).getBody().get("spentUsd")).doubleValue())
            .isEqualTo(0.008);

        // A message about Alice's opportunity; Bob cannot draft one about it.
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Java Senior", "dailyRate", 650, "status", "INTERVIEW",
            "clientId", aliceClientId, "freelanceId", alice.freelanceId(), "techStack", "Java, Spring"), Map.class));
        Map<String, Object> message = Map.of("projectId", project, "kind", "FOLLOW_UP", "language", "FR", "notes", "Rappeler ma dispo");
        assertThat(call(alice, HttpMethod.POST, "/ai/message", message, Map.class).getBody()).containsEntry("subject", "Mission Java Senior")
            .containsEntry("body", "Bonjour,\n\nJe reviens vers vous.\n\nAlice");
        assertThat(BODIES.getLast()).contains("a follow-up asking where the process stands", "Java Senior", "Rappeler ma dispo");
        assertThat(call(bob, HttpMethod.POST, "/ai/message", message, String.class).getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

        // The allowance used up: refused, until the member brings their own key, which no limit applies to.
        assertThat(call(alice, HttpMethod.POST, "/ai/posting", Map.of("text", "Une autre mission"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.GET, "/ai/status", null, Map.class).getBody()).containsEntry("available", false)
            .containsEntry("reason", "ACCOUNT_LIMIT");
        assertThat(call(alice, HttpMethod.PUT, "/ai/key", Map.of("apiKey", "not-a-key"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.PUT, "/ai/key", Map.of("apiKey", "sk-ant-alice-own", "model", "claude-haiku-5-5"), Map.class).getBody())
            .containsEntry("available", true).containsEntry("ownKey", true).containsEntry("model", "claude-haiku-5-5");
        assertThat(jdbcTemplate.queryForObject("SELECT ai_api_key FROM users WHERE email = ?", String.class, aliceEmail))
            .isNotNull().doesNotContain("sk-ant-alice-own");
        assertThat(call(alice, HttpMethod.POST, "/ai/posting", Map.of("text", "Une autre mission"), Map.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(KEYS.getLast()).isEqualTo("sk-ant-alice-own");
        assertThat(BODIES.getLast()).contains("\"model\":\"claude-haiku-5-5\"");

        // The opportunity weighed against the profile: the score kept within 0 to 100, blank points dropped.
        Map<String, Object> fit = call(alice, HttpMethod.POST, "/ai/fit", Map.of("projectId", project, "language", "FR",
            "priorities", "Pas plus de 2 jours sur site"), Map.class).getBody();
        assertThat(fit).containsEntry("score", 100).containsEntry("verdict", "Une bonne mission.").containsEntry("suggestedRate", 700);
        assertThat((List<String>) fit.get("pros")).containsExactly("Stack Java", "Télétravail");
        assertThat(BODIES.getLast()).contains("<profile>", "Java Senior", "Pas plus de 2 jours sur site", "\"effort\":\"medium\"");
        assertThat(call(bob, HttpMethod.POST, "/ai/fit", Map.of("projectId", project), String.class).getStatusCode())
            .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

        // A conversation about the opportunity, kept with it: the history goes back with each question.
        List<Map<String, Object>> chat = call(alice, HttpMethod.POST, "/ai/chat/" + project, Map.of("text", "Comment préparer l'entretien ?"),
            List.class).getBody();
        assertThat(chat).extracting(turn -> turn.get("text")).containsExactly("Comment préparer l'entretien ?",
            "Préparez trois exemples de projets Kafka.");
        call(alice, HttpMethod.POST, "/ai/chat/" + project, Map.of("text", "Et pour le TJM ?"), List.class);
        assertThat(BODIES.getLast()).contains("Comment préparer l'entretien ?", "Préparez trois exemples", "Et pour le TJM ?", "Java Senior");
        assertThat(call(alice, HttpMethod.GET, "/ai/chat/" + project, null, List.class).getBody()).hasSize(4);
        assertThat(call(bob, HttpMethod.GET, "/ai/chat/" + project, null, String.class).getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.DELETE, "/ai/chat/" + project, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/ai/chat/" + project, null, List.class).getBody()).isEmpty();

        // A feed offer triaged and its verdict kept for Alice's workspace only, dropped once the offer is gone.
        long offerId = offers.save(offer()).getId();
        assertThat(call(alice, HttpMethod.GET, "/ai/offers/" + offerId + "/verdict", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        Map<String, Object> verdict = call(alice, HttpMethod.POST, "/ai/offers/" + offerId + "/verdict?language=FR", null, Map.class).getBody();
        assertThat(verdict).containsEntry("goodFit", false).containsEntry("verdict", "Sur site tous les jours.");
        assertThat((List<String>) verdict.get("dealBreakers")).containsExactly("5 jours sur site", "TJM sous l'objectif");
        assertThat(BODIES.getLast()).contains("Lead Kotlin", "<profile>");
        assertThat(call(alice, HttpMethod.GET, "/ai/offers/" + offerId + "/verdict", null, Map.class).getBody()).containsEntry("goodFit", false);
        assertThat(call(bob, HttpMethod.GET, "/ai/offers/" + offerId + "/verdict", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        jdbcTemplate.update("DELETE FROM job_offer_skills WHERE offer_id = ?", offerId);
        offers.deleteById(offerId);
        triageService.dropOrphans();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM offer_ai_verdicts WHERE offer_id = ?", Integer.class, offerId)).isZero();

        // The dossier tailored to the opportunity: its wording changed, nothing added, no name, contact or client sent to the AI.
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650, "status", "WON", "clientId", aliceClientId,
            "startDate", "2025-03-01", "techStack", "Java, Kafka", "description", "Paie migrée vers Kafka."), Map.class);
        ResponseEntity<byte[]> tailored = call(alice, HttpMethod.POST, "/ai/dossier/" + project + "?layout=ANONYMOUS&language=FR", null, byte[].class);
        assertThat(tailored.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(text(tailored.getBody())).contains("Développeur Java tourné vers les flux de paie.", "Migration de la paie vers Kafka")
            .doesNotContain("Inventée", "Cobol", "Paie migrée", aliceEmail);
        assertThat(BODIES.getLast()).contains("You tailor", "Lead Java", "Paie migrée vers Kafka.", "Java Senior")
            .doesNotContain(aliceEmail, "Alice Corp");
        call(alice, HttpMethod.POST, "/ai/dossier/" + project + "?layout=NAMED", null, byte[].class);
        assertThat(BODIES.getLast()).doesNotContain(aliceEmail, "Alice Corp");
        assertThat(call(bob, HttpMethod.POST, "/ai/dossier/" + project, null, String.class).getStatusCode())
            .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

        // A refusal, and a key Anthropic refuses, are told as such.
        assertThat(call(alice, HttpMethod.POST, "/ai/posting", Map.of("text", "PLEASE-REFUSE"), Map.class).getBody())
            .containsEntry("message", "The AI declined this request");
        call(alice, HttpMethod.PUT, "/ai/key", Map.of("apiKey", "sk-ant-refused"), Map.class);
        assertThat(call(alice, HttpMethod.POST, "/ai/posting", Map.of("text", "Une mission"), Map.class).getBody())
            .containsEntry("message", "The AI key was refused: check it");
        assertThat(call(alice, HttpMethod.DELETE, "/ai/key", null, Map.class).getBody()).containsEntry("ownKey", false)
            .containsEntry("available", false);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ai_usage WHERE own_key = true", Integer.class)).isPositive();
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsPdfsPolishesMessagesAndSuggestsRates() throws IOException {
        // Alice's own key: no allowance to mind here.
        call(alice, HttpMethod.PUT, "/ai/key", Map.of("apiKey", "sk-ant-alice-own"), Map.class);
        // A posting saved as a PDF gives its text to check, without AI; anything else is refused.
        org.springframework.util.LinkedMultiValueMap<String, Object> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("file", new org.springframework.core.io.ByteArrayResource(dev.swirlit.indezy.ai.PostingPdfTextTest.pdf(false, "Mission Kafka")) {
            @Override
            public String getFilename() {
                return "annonce.pdf";
            }
        });
        int before = BODIES.size();
        HttpHeaders multipart = new HttpHeaders();
        multipart.setBearerAuth(alice.token());
        multipart.setContentType(MediaType.MULTIPART_FORM_DATA);
        assertThat(exchange("/ai/posting/pdf", HttpMethod.POST, new HttpEntity<>(form, multipart), Map.class).getBody())
            .containsEntry("text", "Mission Kafka").containsEntry("cut", false);
        assertThat(BODIES).hasSize(before);
        org.springframework.util.LinkedMultiValueMap<String, Object> notPdf = new org.springframework.util.LinkedMultiValueMap<>();
        notPdf.add("file", new org.springframework.core.io.ByteArrayResource("hello".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "annonce.pdf";
            }
        });
        assertThat(exchange("/ai/posting/pdf", HttpMethod.POST, new HttpEntity<>(notPdf, multipart), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        // A message polished in the tone asked, its variables kept; one whose variables the AI lost is refused.
        assertThat(call(alice, HttpMethod.POST, "/ai/polish", Map.of("subject", "point mission", "body", "bonjour {{ firstName }}, ou en est on",
            "tone", "FORMAL"), Map.class).getBody()).containsEntry("subject", "Point sur la mission")
            .containsEntry("body", "Bonjour {{firstName}},\n\nOù en est-on ?");
        assertThat(BODIES.getLast()).contains("more formal", "ou en est on");
        assertThat(call(alice, HttpMethod.POST, "/ai/polish", Map.of("body", "bonjour {{firstName}}, {{myName}}"), Map.class).getBody())
            .containsEntry("message", "The AI changed the message's variables: polish it again or edit it by hand");
        assertThat(call(alice, HttpMethod.POST, "/ai/polish", Map.of("body", " "), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // A negotiation prepared: the ESN's margin from its client rate, the walk-away kept at most at the counter-offer.
        long negotiated = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Kafka", "dailyRate", 600, "clientDailyRate", 750,
            "status", "OFFER", "clientId", aliceClientId, "techStack", "Kafka"), Map.class));
        Map<String, Object> preparation = call(alice, HttpMethod.POST, "/ai/negotiation/" + negotiated + "?language=FR", null, Map.class).getBody();
        assertThat(preparation).containsEntry("counterOffer", 680).containsEntry("walkAway", 680).containsEntry("marginEuros", 150)
            .containsEntry("marginPercent", 20).containsEntry("strategy", "Ancrer haut.");
        assertThat((List<String>) preparation.get("arguments")).containsExactly("Kafka rare");
        assertThat(BODIES.getLast()).contains("Rate offered: 600 EUR", "Rate the client pays the ESN: 750 EUR", "20 % of the client rate");
        assertThat(call(bob, HttpMethod.POST, "/ai/negotiation/" + negotiated, null, String.class).getStatusCode())
            .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

        // The availability announced from the day after the running mission ends.
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650, "status", "WON", "clientId", aliceClientId,
            "startDate", java.time.LocalDate.now().minusMonths(1).toString(), "durationInMonths", 3), Map.class);
        Map<String, Object> announcement = call(alice, HttpMethod.POST, "/ai/availability?language=FR", null, Map.class).getBody();
        assertThat(announcement).containsEntry("linkedin", "Disponible bientôt pour une mission Java.").containsEntry("network", "Dispo en janvier.")
            .containsEntry("availableFrom", java.time.LocalDate.now().minusMonths(1).plusMonths(3).toString());
        assertThat(BODIES.getLast()).contains("You write the posts", "Available from: ");

        // A rate suggested for an opportunity being written, its range kept around the rate.
        Map<String, Object> rate = call(alice, HttpMethod.POST, "/ai/rate", Map.of("role", "Lead Kafka", "techStack", "Kafka, Java",
            "workMode", "REMOTE", "durationInMonths", 6, "language", "FR"), Map.class).getBody();
        assertThat(rate).containsEntry("rate", 700).containsEntry("low", 650).containsEntry("high", 700).containsEntry("marketMedian", 0);
        assertThat((List<String>) rate.get("reasons")).containsExactly("Kafka est rare");
        assertThat(BODIES.getLast()).contains("Lead Kafka", "Kafka, Java", "REMOTE", "6 months");
        assertThat(call(alice, HttpMethod.POST, "/ai/rate", Map.of("role", "x".repeat(301)), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
