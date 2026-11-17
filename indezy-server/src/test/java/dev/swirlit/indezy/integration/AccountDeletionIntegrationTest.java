package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.media.NetworkFixtures;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import dev.swirlit.indezy.service.account.AccountDeletionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Account deletion over HTTP: Alice, with a workspace and a network presence, asks for it; her account goes on hold at
 * once (her token stops, sign-in says so, the network no longer shows her), she restores it by signing in, asks again,
 * and 30 days later everything she held is gone, from every table, while Bob keeps his posts and their conversation.
 */
class AccountDeletionIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);
    /** Columns that name the account (or, for freelance_id, its workspace). */
    private static final Set<String> USER_COLUMNS = Set.of("user_id", "author_id", "owner_id", "follower_id", "followed_id", "recipient_id",
        "actor_id", "member_id", "requester_id", "reporter_id", "target_user_id");

    @Autowired
    private FeatureFlagService flags;

    @Autowired
    private AccountDeletionService deletion;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private String join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    private ResponseEntity<Map> login(boolean restore) {
        LoginRequest request = new LoginRequest();
        request.setEmail(aliceEmail);
        request.setPassword(PASSWORD);
        request.setRestore(restore);
        return post("/auth/login", request);
    }

    private Object upload(Account account, String name, byte[] content) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return name;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(account.token());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return ((Map<?, ?>) exchange("/network/files", HttpMethod.POST, new HttpEntity<>(form, headers), Map.class).getBody().get("file"))
            .get("id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void holdsTheAccountThirtyDaysThenDeletesEverythingItHeld() throws Exception {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        long aliceId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, aliceEmail);
        long aliceWorkspace = alice.freelanceId();

        // Alice's workspace and network presence.
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Architecte", "dailyRate", 650, "status", "INTERVIEW",
            "clientId", aliceClientId, "freelanceId", aliceWorkspace), Map.class));
        call(alice, HttpMethod.POST, "/projects/" + project + "/notes", Map.of("content", "Appel avec Léa"), String.class);
        call(alice, HttpMethod.POST, "/interview-steps", Map.of("title", "Entretien technique", "status", "PLANNED", "projectId", project),
            Map.class);
        call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Jean", "lastName", "Dupont", "email", "jean@esn.example",
            "clientId", aliceClientId, "legalBasis", "CONSENT", "freelanceId", aliceWorkspace), Map.class);
        call(alice, HttpMethod.POST, "/seasons", Map.of("name", "Automne 2026", "startDate", "2026-09-01"), Map.class);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        call(alice, HttpMethod.PUT, "/network/members/" + bobHandle + "/follow", null, Map.class);
        Object image = upload(alice, "photo.png", NetworkFixtures.image(20, 20, "png"));
        Object alicePost = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Retour de mission #kafka", "files",
            List.of(Map.of("id", image, "alt", "Un schéma"))), Map.class).getBody().get("id");
        Object bobPost = call(bob, HttpMethod.POST, "/network/posts", Map.of("text", "Une question sur Spark"), Map.class).getBody().get("id");
        call(bob, HttpMethod.PUT, "/network/posts/" + alicePost + "/like", null, Map.class);
        call(bob, HttpMethod.POST, "/network/posts/" + alicePost + "/comments", Map.of("text", "Bravo"), Map.class);
        Object aliceComment = call(alice, HttpMethod.POST, "/network/posts/" + bobPost + "/comments", Map.of("text", "Essaie Flink"), Map.class)
            .getBody().get("id");
        call(bob, HttpMethod.POST, "/network/posts/" + bobPost + "/comments", Map.of("text", "Merci", "parentId", aliceComment), Map.class);
        call(alice, HttpMethod.PUT, "/network/posts/" + bobPost + "/like", null, Map.class);
        Object conversation = call(alice, HttpMethod.POST, "/network/conversations", Map.of("handle", bobHandle), Map.class).getBody().get("id");
        Object document = upload(alice, "tjm.xlsx", NetworkFixtures.archive(Map.of("xl/sharedStrings.xml", "<t>650</t>")));
        call(alice, HttpMethod.POST, "/network/conversations/" + conversation + "/messages", Map.of("text", "Voici mes tarifs", "files",
            List.of(Map.of("id", document))), Map.class);
        call(bob, HttpMethod.POST, "/network/conversations/" + conversation + "/messages", Map.of("text", "Merci Alice"), Map.class);

        // Only the account's own email confirms; then it is on hold at once.
        assertThat(call(alice, HttpMethod.POST, "/users/me/deletion", Map.of("confirmation", "someone@example.com"), Map.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<Map> held = call(alice, HttpMethod.POST, "/users/me/deletion", Map.of("confirmation", " " + aliceEmail.toUpperCase() + " "),
            Map.class);
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(held.getBody().get("purgeOn")).isEqualTo(LocalDate.now().plusDays(30).toString());
        assertThat(call(alice, HttpMethod.GET, "/users/profile", null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(bob, HttpMethod.GET, "/network/members/" + aliceHandle + "/page", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.POST, "/network/conversations/" + conversation + "/messages", Map.of("text", "Tu es là ?"), Map.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Signing in says the account is on hold; with restore, it comes back as it was.
        ResponseEntity<Map> signIn = login(false);
        assertThat(signIn.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(signIn.getBody()).containsEntry("accountOnHold", true).containsEntry("purgeOn", LocalDate.now().plusDays(30).toString())
            .containsEntry("token", null);
        ResponseEntity<Map> restored = login(true);
        assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
        Account back = new Account((String) restored.getBody().get("token"), aliceWorkspace);
        assertThat(call(back, HttpMethod.GET, "/projects/" + project, null, Map.class).getBody()).containsEntry("role", "Architecte");
        assertThat(call(bob, HttpMethod.GET, "/network/members/" + aliceHandle + "/page", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForList("SELECT type FROM account_events WHERE user_id = ? ORDER BY id", String.class, aliceId))
            .contains("ACCOUNT_DELETION_REQUESTED", "ACCOUNT_RESTORED");

        // Asked again: nothing goes while the 30 days run; once over, everything does.
        call(back, HttpMethod.POST, "/users/me/deletion", Map.of("confirmation", aliceEmail), Map.class);
        deletion.purgeDue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, aliceId)).isOne();
        jdbcTemplate.update("UPDATE users SET deleted_at = DATEADD('DAY', -31, deleted_at) WHERE id = ?", aliceId);
        assertThat(login(true).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(deletion.purgeDue()).isGreaterThanOrEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, aliceId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM freelances WHERE id = ?", Integer.class, aliceWorkspace)).isZero();
        for (Map<String, Object> column : jdbcTemplate.queryForList("SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
            "WHERE TABLE_SCHEMA = 'PUBLIC'")) {
            String table = column.get("TABLE_NAME").toString().toLowerCase();
            String name = column.get("COLUMN_NAME").toString().toLowerCase();
            Long owner = USER_COLUMNS.contains(name) ? Long.valueOf(aliceId) : name.equals("freelance_id") ? Long.valueOf(aliceWorkspace) : null;
            if (owner != null && !table.equals("network_messages") && !table.equals("network_files")) {
                assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + name + " = ?", Integer.class, owner))
                    .as(table + "." + name).isZero();
            }
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM network_posts WHERE id = ?", Integer.class, alicePost)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM network_comments WHERE id = ?", Integer.class, aliceComment)).isZero();

        // Bob keeps his post and the conversation, Alice's messages in it from a former member, her document with them.
        assertThat(call(bob, HttpMethod.GET, "/network/posts/" + bobPost, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> thread = call(bob, HttpMethod.GET, "/network/conversations/" + conversation, null, Map.class).getBody();
        assertThat((Map<String, Object>) thread.get("conversation")).containsEntry("with", null).containsEntry("closed", "LEFT");
        List<Map<String, Object>> messages = (List<Map<String, Object>>) thread.get("messages");
        assertThat(messages).extracting(message -> message.get("text")).containsExactly("Voici mes tarifs", "Merci Alice");
        assertThat((List<Object>) messages.getFirst().get("files")).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM network_files WHERE owner_id = ? AND message_id IS NULL",
            Integer.class, aliceId)).isZero();
    }
}
