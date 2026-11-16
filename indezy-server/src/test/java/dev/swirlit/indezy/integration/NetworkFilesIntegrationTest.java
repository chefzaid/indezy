package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.media.NetworkFixtures;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import dev.swirlit.indezy.service.NetworkFileService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Files over HTTP: Alice adds an image (described) and a PDF naming her client to a post, which Bob reads through the
 * signed addresses; she sends Bob a document in a message; the limits and the checks; a deleted post or message takes
 * its files; a post held for review keeps its files until an administrator publishes it.
 */
@TestPropertySource(properties = "indezy.admin.emails=" + AdminIntegrationTest.CHIEF)
class NetworkFilesIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @Autowired
    private NetworkFileService fileService;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private String join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    private ResponseEntity<Map> upload(Account account, String name, byte[] content) {
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
        return exchange("/network/files", HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> file(ResponseEntity<Map> uploaded) {
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Map<String, Object>) uploaded.getBody().get("file");
    }

    private ResponseEntity<byte[]> open(Object address) {
        return exchange(address.toString(), HttpMethod.GET, HttpEntity.EMPTY, byte[].class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void carriesImagesAndDocumentsThroughSignedAddresses() throws Exception {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);

        // An image made anew; a PDF naming Alice's client, pointed out before it goes; another file refused.
        Map<String, Object> image = file(upload(alice, "photo.jpeg", NetworkFixtures.image(3000, 1500, "jpg")));
        assertThat(image).containsEntry("kind", "IMAGE").containsEntry("width", 2048).containsEntry("height", 1024)
            .containsEntry("name", "photo.jpg").containsEntry("previewUrl", null);
        ResponseEntity<Map> pdfUpload = upload(alice, "mission.pdf", NetworkFixtures.pdf("Mission chez Alice Corp a Paris", 2));
        Map<String, Object> pdf = file(pdfUpload);
        assertThat(pdf).containsEntry("kind", "DOCUMENT").containsEntry("pages", 2).containsEntry("contentType", "application/pdf");
        assertThat((List<Map<String, Object>>) pdfUpload.getBody().get("confidential"))
            .anySatisfy(match -> assertThat(match).containsEntry("found", "Alice Corp").containsEntry("kind", "CLIENT"));
        assertThat(upload(alice, "notes.exe", "MZ binary".getBytes()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(bob, "x.docm", NetworkFixtures.archive(Map.of("a", "b"))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Each image described; Bob cannot attach Alice's file; 4 images at most.
        Map<String, Object> undescribed = Map.of("text", "Retour de mission", "files", List.of(Map.of("id", image.get("id"))));
        assertThat(call(alice, HttpMethod.POST, "/network/posts", undescribed, Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(bob, HttpMethod.POST, "/network/posts", Map.of("text", "Pas à moi", "files",
            List.of(Map.of("id", pdf.get("id")))), Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        List<Map<String, Object>> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            five.add(Map.of("id", file(upload(bob, "p" + i + ".png", NetworkFixtures.image(10, 10, "png"))).get("id"), "alt", "Carré"));
        }
        assertThat(call(bob, HttpMethod.POST, "/network/posts", Map.of("text", "Cinq", "files", five), Map.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(bob, HttpMethod.DELETE, "/network/files/" + five.getFirst().get("id"), null, Void.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(bob, HttpMethod.DELETE, "/network/files/" + pdf.get("id"), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // The post carries both; Bob reads them through their signed addresses.
        Map<String, Object> post = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Retour de mission", "files",
            List.of(Map.of("id", image.get("id"), "alt", " Le tableau blanc "), Map.of("id", pdf.get("id")))), Map.class).getBody();
        List<Map<String, Object>> files = (List<Map<String, Object>>) post.get("files");
        assertThat(files).extracting(found -> found.get("kind")).containsExactly("IMAGE", "DOCUMENT");
        assertThat(files.getFirst()).containsEntry("alt", "Le tableau blanc");
        Map<String, Object> read = ((List<Map<String, Object>>) call(bob, HttpMethod.GET, "/network/members/" + aliceHandle + "/posts",
            null, Map.class).getBody().get("posts")).getFirst();
        List<Map<String, Object>> seen = (List<Map<String, Object>>) read.get("files");
        ResponseEntity<byte[]> shown = open(seen.getFirst().get("url"));
        assertThat(shown.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(shown.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(shown.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(shown.getHeaders().getCacheControl()).contains("private").contains("max-age=");
        ResponseEntity<byte[]> document = open(seen.get(1).get("url"));
        assertThat(document.getHeaders().getContentDisposition().isInline()).isTrue();
        assertThat(new String(document.getBody(), 0, 5)).isEqualTo("%PDF-");
        assertThat(open(seen.get(1).get("previewUrl")).getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        String tampered = seen.getFirst().get("url").toString().replaceAll("s=.*$", "s=forged");
        assertThat(open(tampered).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(open(seen.getFirst().get("url").toString().replace("/full?", "/other?")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(open(seen.getFirst().get("url").toString().replace("/full?", "/preview?")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(file(upload(alice, "slides.pptx", NetworkFixtures.archive(Map.of("ppt/slides/slide1.xml", "<a:t>Kafka</a:t>")))))
            .containsEntry("previewUrl", null);

        // In a message: a document alone; Bob downloads it; Alice deletes the message, and the file goes with it.
        Object conversation = call(alice, HttpMethod.POST, "/network/conversations", Map.of("handle", bobHandle), Map.class).getBody().get("id");
        Map<String, Object> rates = file(upload(alice, "rates.xlsx", NetworkFixtures.archive(Map.of("xl/sharedStrings.xml", "<t>650</t>"))));
        Map<String, Object> message = call(alice, HttpMethod.POST, "/network/conversations/" + conversation + "/messages",
            Map.of("files", List.of(Map.of("id", rates.get("id")))), Map.class).getBody();
        Map<String, Object> received = ((List<Map<String, Object>>) call(bob, HttpMethod.GET, "/network/conversations/" + conversation, null,
            Map.class).getBody().get("messages")).getFirst();
        Object rateUrl = ((List<Map<String, Object>>) received.get("files")).getFirst().get("url");
        assertThat(open(rateUrl).getHeaders().getContentDisposition().isAttachment()).isTrue();
        call(alice, HttpMethod.DELETE, "/network/messages/" + message.get("id"), null, Map.class);
        assertThat(open(rateUrl).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // A deleted post takes its files.
        call(alice, HttpMethod.DELETE, "/network/posts/" + post.get("id"), null, Void.class);
        assertThat(open(seen.getFirst().get("url")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsTheFilesOfAHeldPostUntilItIsPublishedOrRejected() throws Exception {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String bobHandle = join(bob);
        Account admin = chief();
        String text = "Formation Kafka " + UUID.randomUUID();
        call(bob, HttpMethod.POST, "/network/posts", Map.of("text", text), Map.class);
        Object kept = file(upload(bob, "a.png", NetworkFixtures.image(20, 20, "png"))).get("id");
        Object dropped = file(upload(bob, "b.png", NetworkFixtures.image(20, 20, "png"))).get("id");
        assertThat(call(bob, HttpMethod.POST, "/network/posts", Map.of("text", text.toUpperCase(), "files",
            List.of(Map.of("id", kept, "alt", "Un schéma"))), Map.class).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(call(bob, HttpMethod.POST, "/network/posts", Map.of("text", text.toLowerCase(), "files",
            List.of(Map.of("id", dropped, "alt", "Un autre"))), Map.class).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        List<Map<String, Object>> held = (List<Map<String, Object>>) call(admin, HttpMethod.GET, "/admin/network/reports", null, Map.class)
            .getBody().get("held");
        Object published = held.stream().filter(item -> text.toUpperCase().equals(item.get("text"))).findFirst().orElseThrow().get("id");
        Object rejected = held.stream().filter(item -> text.toLowerCase().equals(item.get("text"))).findFirst().orElseThrow().get("id");
        call(admin, HttpMethod.PUT, "/admin/network/held/" + published + "?publish=true", null, Map.class);
        call(admin, HttpMethod.PUT, "/admin/network/held/" + rejected + "?publish=false", null, Map.class);
        List<Map<String, Object>> posts = (List<Map<String, Object>>) call(bob, HttpMethod.GET, "/network/members/" + bobHandle + "/posts",
            null, Map.class).getBody().get("posts");
        assertThat(posts).filteredOn(post -> text.toUpperCase().equals(post.get("text"))).singleElement()
            .satisfies(post -> assertThat((List<Map<String, Object>>) post.get("files")).singleElement()
                .satisfies(found -> assertThat(found).containsEntry("id", kept).containsEntry("alt", "Un schéma")));
        // The rejected post's file is gone; the nightly clean-up drops nothing attached.
        assertThat(call(bob, HttpMethod.DELETE, "/network/files/" + dropped, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        fileService.dropUnattached();
        assertThat(posts).isNotEmpty();
    }

    private Account chief() {
        LoginRequest request = new LoginRequest();
        request.setEmail(AdminIntegrationTest.CHIEF);
        request.setPassword(PASSWORD);
        ResponseEntity<Map> signedIn = post("/auth/login", request);
        if (signedIn.getStatusCode() != HttpStatus.OK) {
            registerAs(AdminIntegrationTest.CHIEF);
            signedIn = post("/auth/login", request);
        }
        Map<?, ?> user = (Map<?, ?>) signedIn.getBody().get("user");
        return new Account((String) signedIn.getBody().get("token"), ((Number) user.get("id")).longValue());
    }
}
