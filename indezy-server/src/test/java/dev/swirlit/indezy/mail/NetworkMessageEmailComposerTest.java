package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkMessageEmailComposerTest {

    private final NetworkMessageEmailComposer composer = new NetworkMessageEmailComposer(
        new MailProperties("re_test", "Indezy <noreply@swirlit.dev>", "https://indezy.example/", "https://api.resend.com"));

    @Test
    void saysWhoWroteAndHowManyMessagesInFrenchWithoutTheirText() {
        Email email = composer.compose(new NetworkMessageEmailComposer.Unread(4L, "Camille", "camille@example.com", DocumentLanguage.FR,
            "tok", List.of(new NetworkMessageEmailComposer.Waiting("Léo <Martin>", 1), new NetworkMessageEmailComposer.Waiting("Inès", 3)),
            "network-messages/4/1"));
        assertThat(email.subject()).isEqualTo("4 messages non lus sur Indezy");
        assertThat(email.text()).startsWith("Bonjour Camille,").contains("- Léo <Martin> · 1 message", "- Inès · 3 messages", "Lire mes messages");
        assertThat(email.html()).contains("Léo &lt;Martin&gt;").doesNotContain("<Martin>");
        assertThat(email.headers()).containsEntry("List-Unsubscribe", "<https://indezy.example/api/public/network-messages/unsubscribe/tok>");
        assertThat(email.idempotencyKey()).isEqualTo("network-messages/4/1");
        assertThat(email.toString()).doesNotContain("camille@example.com");

        Email one = composer.compose(new NetworkMessageEmailComposer.Unread(4L, null, "camille@example.com", DocumentLanguage.FR, "tok",
            List.of(new NetworkMessageEmailComposer.Waiting("Léo", 1)), "network-messages/4/2"));
        assertThat(one.subject()).isEqualTo("1 message non lu sur Indezy");
        assertThat(one.text()).startsWith("Bonjour,");
        assertThat(composer.compose(new NetworkMessageEmailComposer.Unread(4L, " ", "x@example.com", DocumentLanguage.EN, "tok",
            List.of(new NetworkMessageEmailComposer.Waiting("Léo", 2)), "k")).text()).startsWith("Hello,");
    }
}
