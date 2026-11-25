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
            } else if (body.contains("You prepare a French freelance IT consultant's application")) {
                answer = message("{\"subject\":\"Candidature Lead Kotlin\",\"message\":\"" + "Bonjour ".repeat(60) + "\"," +
                    "\"screening\":[{\"question\":\"Disponibilité\",\"answer\":\"Dès janvier.\"},{\"question\":\" \",\"answer\":\"x\"}]," +
                    "\"rate\":620,\"rateReasons\":[\"Kotlin rare\",\" \"]}", "end_turn");
            } else if (body.contains("CV into their profile")) {
                answer = message("{\"firstName\":\"Alice\",\"lastName\":\" Martin \",\"phone\":\"\",\"city\":\"Lyon\",\"position\":\"Lead Java\"," +
                    "\"company\":null,\"bio\":\"Dix ans de Java.\",\"website\":null,\"linkedin\":null,\"github\":null," +
                    "\"languages\":[\"Anglais\",\" \"],\"skills\":[{\"name\":\"Java\",\"years\":10,\"level\":\"EXPERT\"},{\"name\":\"java\",\"years\":3}," +
                    "{\"name\":\"Kafka\",\"years\":99}],\"certifications\":[{\"name\":\"AWS Solutions Architect\",\"authority\":\" \"," +
                    "\"obtainedOn\":\"2024-03\",\"expiresOn\":\"bientôt\"},{\"name\":\"aws solutions architect\"},{\"name\":\" \"}]," +
                    "\"positions\":[{\"employer\":\"Banque X\",\"title\":\"Dev Java\",\"startsOn\":\"2015-09\",\"endsOn\":\"\"},{\"employer\":\" \"," +
                    "\"title\":\"Sans employeur\"}],\"education\":[{\"school\":\"INSA Lyon\",\"degree\":\"\",\"startYear\":2010,\"endYear\":3000}]}", "end_turn");
            } else if (body.contains("You turn what a French freelance")) {
                answer = message("{\"skills\":[\"Java\",\" Kafka \",\"Java\"],\"rateMin\":650,\"minRemoteDays\":2,\"workModes\":[\"HYBRID\"]," +
                    "\"regions\":[\"Île-de-France\",\"Atlantide\"],\"startBefore\":\"2027-03-01\",\"durationMin\":0,\"keywords\":\"\"," +
                    "\"avoid\":[\"banque\"]}", "end_turn");
            } else if (body.contains("You read the debrief")) {
                answer = message("{\"outcome\":\"NEXT_ROUND\",\"nextRound\":\" Entretien technique \",\"nextSteps\":[\"Envoyer deux références\",\" \"]," +
                    "\"thankYouSubject\":\"Merci pour l'échange\",\"thankYouBody\":\" Bonjour, merci pour ce temps. \"}", "end_turn");
            } else if (body.contains("You coach")) {
                answer = message("{\"technical\":[{\"question\":\"Kafka exactly-once ?\",\"hint\":\"Idempotence.\"},{\"question\":\" \",\"hint\":\"x\"}]," +
                    "\"behavioural\":[{\"question\":\"Un conflit ?\",\"hint\":null}],\"pitch\":\" Bonjour, je suis Alice. \",\"toAsk\":[\"Taille de l'équipe ?\",\"\"]}",
                    "end_turn");
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
                answer = message("{\"goodFit\":false,\"verdict\":\"Sur site tous les jours.\",\"dealBreakers\":[\"5 jours sur site\",\"\",\"TJM sous l'objectif\"]," +
                    "\"likelyClient\":\" une grande banque à Paris\\n\"}",
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

    @Autowired
    private dev.swirlit.indezy.repository.AutopilotDraftRepository autopilotDrafts;

    @Autowired
    private dev.swirlit.indezy.repository.AutopilotPackRepository autopilotPacks;

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
        assertThat(verdict).containsEntry("likelyClient", "une grande banque à Paris");
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
    void choosesAModelPerTaskWithTheMembersOwnKey() {
        // Without their own key, a member keeps Indezy's choices.
        assertThat(call(bob, HttpMethod.PUT, "/ai/key/models", Map.of("models", Map.of("MESSAGE", "claude-opus-5-5")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        call(bob, HttpMethod.PUT, "/ai/key", Map.of("apiKey", "sk-ant-bob-own"), Map.class);
        assertThat(call(bob, HttpMethod.PUT, "/ai/key/models", Map.of("models", Map.of("MESSAGE", "gpt-5")), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> status = call(bob, HttpMethod.PUT, "/ai/key/models", Map.of("models", Map.of("MESSAGE", "claude-opus-5-5")),
            Map.class).getBody();
        assertThat((Map<String, Object>) status.get("chosen")).containsExactly(Map.entry("MESSAGE", "claude-opus-5-5"));
        assertThat((Map<String, Object>) status.get("models")).containsEntry("MESSAGE", "claude-opus-5-5");
        call(bob, HttpMethod.POST, "/ai/polish", Map.of("subject", "point", "body", "bonjour {{ firstName }}, ou en est on", "tone", "FORMAL"),
            Map.class);
        assertThat(BODIES.getLast()).contains("\"model\":\"claude-opus-5-5\"");
        // Going back to Indezy's key forgets the choices.
        assertThat((Map<String, Object>) call(bob, HttpMethod.DELETE, "/ai/key", null, Map.class).getBody().get("chosen")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsWhatTheAutopilotPreparedOvernight() {
        call(alice, HttpMethod.PUT, "/ai/key", Map.of("apiKey", "sk-ant-alice-own"), Map.class);
        long offerId = offers.save(offer()).getId();
        call(alice, HttpMethod.POST, "/ai/offers/" + offerId + "/verdict?language=FR", null, Map.class);
        // The morning's list: the offers triaged in the last day, the autopilot off until turned on.
        Map<String, Object> queue = call(alice, HttpMethod.GET, "/ai/autopilot", null, Map.class).getBody();
        assertThat(queue).containsEntry("on", false);
        assertThat((List<Map<String, Object>>) queue.get("offers")).singleElement().satisfies(item ->
            assertThat(item).containsEntry("offerId", (int) offerId).containsEntry("goodFit", false).containsEntry("title", "Lead Kotlin"));
        assertThat(call(alice, HttpMethod.PUT, "/users/preferences", Map.of("autopilot", true), Map.class).getBody()).containsEntry("autopilot", true);
        assertThat(call(alice, HttpMethod.GET, "/ai/autopilot", null, Map.class).getBody()).containsEntry("on", true);
        assertThat((List<?>) call(bob, HttpMethod.GET, "/ai/autopilot", null, Map.class).getBody().get("offers")).isEmpty();
        // A follow-up drafted overnight for one of Alice's contacts: listed for her, set aside by her only.
        long lea = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Léa", "lastName", "Suivi", "clientId", aliceClientId), Map.class));
        dev.swirlit.indezy.model.AutopilotDraft draft = new dev.swirlit.indezy.model.AutopilotDraft();
        draft.setFreelanceId(alice.freelanceId());
        draft.setContactId(lea);
        draft.setSubject("Point sur la mission");
        draft.setBody("Bonjour Léa,");
        long draftId = autopilotDrafts.save(draft).getId();
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/ai/autopilot", null, Map.class).getBody().get("drafts"))
            .singleElement().satisfies(item -> assertThat(item).containsEntry("contact", "Léa Suivi").containsEntry("subject", "Point sur la mission").containsEntry("answer", false));
        assertThat(call(bob, HttpMethod.DELETE, "/ai/autopilot/drafts/" + draftId, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.DELETE, "/ai/autopilot/drafts/" + draftId, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat((List<?>) call(alice, HttpMethod.GET, "/ai/autopilot", null, Map.class).getBody().get("drafts")).isEmpty();
        // Léa answering after a follow-up was drafted: the draft leaves the list, pointless now.
        dev.swirlit.indezy.model.AutopilotDraft again = new dev.swirlit.indezy.model.AutopilotDraft();
        again.setFreelanceId(alice.freelanceId());
        again.setContactId(lea);
        again.setSubject("Point sur la mission");
        again.setBody("Bonjour Léa,");
        autopilotDrafts.save(again);
        assertThat((List<?>) call(alice, HttpMethod.GET, "/ai/autopilot", null, Map.class).getBody().get("drafts")).hasSize(1);
        call(alice, HttpMethod.POST, "/contacts/" + lea + "/interactions", Map.of("occurredOn", java.time.LocalDate.now().toString(),
            "channel", "PHONE", "direction", "INCOMING"), Map.class);
        assertThat((List<?>) call(alice, HttpMethod.GET, "/ai/autopilot", null, Map.class).getBody().get("drafts")).isEmpty();
        // An application prepared overnight for the offer: listed with its rate, set aside.
        dev.swirlit.indezy.model.AutopilotPack pack = new dev.swirlit.indezy.model.AutopilotPack();
        pack.setFreelanceId(alice.freelanceId());
        pack.setOfferId(offerId);
        pack.setSubject("Candidature Lead Kotlin");
        pack.setMessage("Bonjour,");
        pack.setScreening("Disponibilité — Janvier");
        pack.setRate(650);
        long packId = autopilotPacks.save(pack).getId();
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/ai/autopilot", null, Map.class).getBody().get("packs"))
            .singleElement().satisfies(item -> assertThat(item).containsEntry("title", "Lead Kotlin").containsEntry("rate", 650)
                .containsEntry("screening", List.of("Disponibilité — Janvier")));
        assertThat(call(bob, HttpMethod.DELETE, "/ai/autopilot/packs/" + packId, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.DELETE, "/ai/autopilot/packs/" + packId, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        jdbcTemplate.update("DELETE FROM offer_ai_verdicts WHERE offer_id = ?", offerId);
        jdbcTemplate.update("DELETE FROM job_offer_skills WHERE offer_id = ?", offerId);
        offers.deleteById(offerId);
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

        // An application pack for a feed offer: the LinkedIn message cut to 300 characters, without a subject; an email keeps it.
        long packed = offers.save(offer()).getId();
        Map<String, Object> linkedin = call(alice, HttpMethod.POST, "/ai/offers/" + packed + "/pack?channel=LINKEDIN&language=FR", null,
            Map.class).getBody();
        assertThat((String) linkedin.get("message")).hasSizeLessThanOrEqualTo(300).endsWith("…");
        assertThat(linkedin).containsEntry("subject", "").containsEntry("rate", 620);
        assertThat((List<Map<String, Object>>) linkedin.get("screening")).hasSize(1);
        assertThat((List<String>) linkedin.get("rateReasons")).containsExactly("Kotlin rare");
        assertThat(BODIES.getLast()).contains("a LinkedIn message of 300 characters", "Lead Kotlin");
        assertThat(call(alice, HttpMethod.POST, "/ai/offers/" + packed + "/pack", null, Map.class).getBody())
            .containsEntry("subject", "Candidature Lead Kotlin");
        jdbcTemplate.update("DELETE FROM job_offer_skills WHERE offer_id = ?", packed);
        offers.deleteById(packed);

        // The next interview prepared from the opportunity, its steps and the profile; blank points dropped.
        Map<String, Object> interview = call(alice, HttpMethod.POST, "/ai/interview/" + negotiated + "?language=FR", null, Map.class).getBody();
        assertThat((List<Map<String, Object>>) interview.get("technical")).extracting(question -> question.get("question"))
            .containsExactly("Kafka exactly-once ?");
        assertThat((List<Map<String, Object>>) interview.get("behavioural")).first().asInstanceOf(
            org.assertj.core.api.InstanceOfAssertFactories.MAP).containsEntry("hint", "");
        assertThat(interview).containsEntry("pitch", "Bonjour, je suis Alice.");
        assertThat((List<String>) interview.get("toAsk")).containsExactly("Taille de l'équipe ?");
        assertThat(BODIES.getLast()).contains("You coach", "Lead Kafka", "<steps>");
        // The debrief turned into what comes next: the next round named, blank steps dropped, the thank-you trimmed.
        Map<String, Object> debrief = call(alice, HttpMethod.POST, "/ai/interview/" + negotiated + "/debrief?language=FR",
            Map.of("notes", "Bon feeling, un entretien technique la semaine prochaine, ils veulent deux références."), Map.class).getBody();
        assertThat(debrief).containsEntry("outcome", "NEXT_ROUND").containsEntry("nextRound", "Entretien technique").containsEntry("reason", null)
            .containsEntry("thankYouSubject", "Merci pour l'échange").containsEntry("thankYouBody", "Bonjour, merci pour ce temps.");
        assertThat((List<String>) debrief.get("nextSteps")).containsExactly("Envoyer deux références");
        assertThat(BODIES.getLast()).contains("<debrief>", "deux références", "Lead Kafka");
        assertThat(call(alice, HttpMethod.POST, "/ai/interview/" + negotiated + "/debrief", Map.of("notes", " "), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(bob, HttpMethod.POST, "/ai/interview/" + negotiated + "/debrief", Map.of("notes", "x"), String.class).getStatusCode())
            .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

        // The availability announced from the day after the running mission ends.
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650, "status", "WON", "clientId", aliceClientId,
            "startDate", java.time.LocalDate.now().minusMonths(1).toString(), "durationInMonths", 3), Map.class);
        Map<String, Object> announcement = call(alice, HttpMethod.POST, "/ai/availability?language=FR", null, Map.class).getBody();
        assertThat(announcement).containsEntry("linkedin", "Disponible bientôt pour une mission Java.").containsEntry("network", "Dispo en janvier.")
            .containsEntry("availableFrom", java.time.LocalDate.now().minusMonths(1).plusMonths(3).toString());
        assertThat(BODIES.getLast()).contains("You write the posts", "Available from: ");

        // A CV read into the profile's fields, nothing kept; a file that is not a CV is refused.
        org.springframework.util.LinkedMultiValueMap<String, Object> cv = new org.springframework.util.LinkedMultiValueMap<>();
        cv.add("file", new org.springframework.core.io.ByteArrayResource(dev.swirlit.indezy.ai.PostingPdfTextTest.pdf(false, "Alice Martin",
            "Lead Java, 10 ans")) {
            @Override
            public String getFilename() {
                return "cv.pdf";
            }
        });
        Map<String, Object> read = exchange("/ai/profile-import", HttpMethod.POST, new HttpEntity<>(cv, multipart), Map.class).getBody();
        assertThat(read).containsEntry("firstName", "Alice").containsEntry("lastName", "Martin").containsEntry("phone", null)
            .containsEntry("position", "Lead Java").containsEntry("languages", List.of("Anglais"));
        assertThat((List<Map<String, Object>>) read.get("skills")).extracting(skill -> skill.get("name") + ":" + skill.get("years"))
            .containsExactly("Java:10", "Kafka:50");
        assertThat((List<Map<String, Object>>) read.get("skills")).extracting(skill -> skill.get("level")).containsExactly("EXPERT", null);
        // Certifications named once, their dates read where they can be.
        assertThat((List<Map<String, Object>>) read.get("certifications")).extracting(certification -> certification.get("name") + ":" +
            certification.get("authority") + ":" + certification.get("obtainedOn") + ":" + certification.get("expiresOn"))
            .containsExactly("AWS Solutions Architect:null:2024-03-01:null");
        assertThat((List<Map<String, Object>>) read.get("positions")).extracting(position -> position.get("employer") + ":" +
            position.get("startsOn") + ":" + position.get("endsOn")).containsExactly("Banque X:2015-09-01:null");
        assertThat((List<Map<String, Object>>) read.get("education")).extracting(school -> school.get("school") + ":" + school.get("degree") +
            ":" + school.get("startYear") + ":" + school.get("endYear")).containsExactly("INSA Lyon:null:2010:0");
        assertThat(BODIES.getLast()).contains("<cv>", "Lead Java, 10 ans");
        // A CV in Word's old format is read for its text too.
        org.springframework.util.LinkedMultiValueMap<String, Object> doc = new org.springframework.util.LinkedMultiValueMap<>();
        doc.add("file", new org.springframework.core.io.ByteArrayResource(dev.swirlit.indezy.media.WordDocTextTest.cv()) {
            @Override
            public String getFilename() {
                return "cv-alice.doc";
            }
        });
        assertThat(exchange("/ai/profile-import", HttpMethod.POST, new HttpEntity<>(doc, multipart), Map.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(BODIES.getLast()).contains("<cv>", "Lead Java freelance", "Compétences : Java, Kafka");
        assertThat(exchange("/ai/profile-import", HttpMethod.POST, new HttpEntity<>(notPdf, multipart), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        // What Alice looks for, read into the feed's filters: the regions by their code, what was not said left out.
        Map<String, Object> brief = call(alice, HttpMethod.POST, "/ai/offer-brief", Map.of("text", "Java Kafka, 650 € minimum, 2 jours de télétravail, " +
            "Île-de-France, pas de banque, avant mars"), Map.class).getBody();
        assertThat((List<String>) brief.get("skills")).containsExactly("Java", "Kafka");
        assertThat(brief).containsEntry("rateMin", 650).containsEntry("minRemoteDays", 2).containsEntry("regions", List.of("11"))
            .containsEntry("startBefore", "2027-03-01").containsEntry("durationMin", null).containsEntry("q", null)
            .containsEntry("avoid", List.of("banque")).containsEntry("workModes", List.of("HYBRID"));
        assertThat(call(alice, HttpMethod.POST, "/ai/offer-brief", Map.of("text", " "), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

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
