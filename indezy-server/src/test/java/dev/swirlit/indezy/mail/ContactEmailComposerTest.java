package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContactEmailComposerTest {

    private final ContactEmailComposer composer = new ContactEmailComposer(new MailProperties(null, "Indezy <noreply@swirlit.dev>",
        "https://indezy.example/", "https://api.resend.com"));

    @Test
    void saysInFrenchWhoSentItAndThatItTellsTheOpening() {
        Email email = composer.compose("lea@acme.example", " Relance ", "Bonjour,\r\n\r\nOù en est-on ?", new ContactEmailComposer.Sender("Alice Martin", "alice@example.com"),
            DocumentLanguage.FR, "t".repeat(43), "k");
        assertThat(email.subject()).isEqualTo("Relance");
        assertThat(email.html()).contains("<p>Bonjour,</p><p>Où en est-on ?</p>",
            "Alice Martin vous a envoyé cet e-mail via Indezy, qui l&#39;informe de son ouverture.",
            "src=\"https://indezy.example/api/public/emails/" + "t".repeat(43) + ".gif\"");
        assertThat(email.text()).endsWith("--\nAlice Martin vous a envoyé cet e-mail via Indezy, qui l'informe de son ouverture." +
            " Répondre à ce message répond directement à Alice Martin.\n");
        assertThat(email.senderName()).isEqualTo("Alice Martin");
        assertThat(email.replyTo()).isEqualTo("alice@example.com");
    }

    @Test
    void saysNothingOfOpeningWithoutTracking() {
        Email email = composer.compose("lea@acme.example", "Relance", "Bonjour", new ContactEmailComposer.Sender("Alice", "alice@example.com"),
            DocumentLanguage.FR, null, "k");
        assertThat(email.html()).doesNotContain("<img", "ouverture");
        assertThat(ContactEmailComposer.notice("Alice", DocumentLanguage.EN, false)).isEqualTo(
            "Alice sent you this email through Indezy. Replying answers Alice directly.");
    }
}
