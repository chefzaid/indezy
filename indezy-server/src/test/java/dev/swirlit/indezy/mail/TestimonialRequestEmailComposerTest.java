package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TestimonialRequestEmailComposerTest {

    private final TestimonialRequestEmailComposer composer = new TestimonialRequestEmailComposer(
        new MailProperties("re_test", "Indezy <noreply@swirlit.dev>", "https://indezy.example/", "https://api.resend.test"));
    private final Instant until = Instant.parse("2026-12-18T10:00:00Z");

    @Test
    void asksInFrenchWithTheLinkAndUntilWhenItOpens() {
        Email email = composer.compose("Marie Durand", "marie@acme.example", "Camille <Dupont>", DocumentLanguage.FR, "tok3n", until, "k-1");

        assertThat(email.to()).isEqualTo("marie@acme.example");
        assertThat(email.subject()).isEqualTo("Camille <Dupont> vous demande une recommandation");
        assertThat(email.text()).contains("Bonjour Marie Durand,", "https://indezy.example/testimonial#tok3n", "18 décembre 2026",
            "sans créer de compte", "Ignorez-le");
        // The names are escaped in the HTML.
        assertThat(email.html()).contains("href=\"https://indezy.example/testimonial#tok3n\"", "Camille &lt;Dupont&gt;")
            .doesNotContain("<Dupont>");
        assertThat(email.idempotencyKey()).isEqualTo("k-1");
    }

    @Test
    void asksInEnglish() {
        Email email = composer.compose("Marie Durand", "marie@acme.example", "Camille Dupont", DocumentLanguage.EN, "tok3n", until, "k-1");

        assertThat(email.subject()).isEqualTo("Camille Dupont asks you for a recommendation");
        assertThat(email.text()).contains("Hello Marie Durand,", "December 18, 2026", "without creating an account");
    }
}
