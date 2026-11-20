package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkNoticeEmailComposerTest {

    private final NetworkNoticeEmailComposer composer = new NetworkNoticeEmailComposer(
        new MailProperties("re_test", "Indezy <noreply@swirlit.dev>", "https://indezy.example/", "https://api.resend.com"));

    @Test
    void tellsWhoDidWhatAsOnTheNotificationsPage() {
        Email email = composer.compose(new NetworkNoticeEmailComposer.Notices("Camille", "camille@example.com", DocumentLanguage.FR, "tok",
            List.of(new NetworkNoticeEmailComposer.Line(NetworkNotificationType.LIKE, "Léo Martin", 2, "Retour sur Kafka"),
                new NetworkNoticeEmailComposer.Line(NetworkNotificationType.FOLLOW, "Inès <B>", 0, null)), "network-notices/4/1"));
        assertThat(email.subject()).isEqualTo("Du nouveau sur le réseau");
        assertThat(email.text()).contains("- Léo Martin et 2 autres ont aimé votre publication “Retour sur Kafka”")
            .contains("- Inès <B> vous suit désormais").contains("Voir mes notifications");
        assertThat(email.html()).contains("Inès &lt;B&gt;").doesNotContain("<B>");
        assertThat(email.headers()).containsEntry("List-Unsubscribe", "<https://indezy.example/api/public/network-notifications/unsubscribe/tok>");
        assertThat(email.toString()).doesNotContain("camille@example.com");
    }

    @Test
    void writesEveryKindInBothLanguages() {
        for (NetworkNotificationType type : NetworkNotificationType.values()) {
            if (type == NetworkNotificationType.IDEA_STATUS) {
                continue;
            }
            assertThat(NetworkNoticeEmailComposer.sentence(new NetworkNoticeEmailComposer.Line(type, "Léo", 0, null), true)).startsWith("Léo ");
            assertThat(NetworkNoticeEmailComposer.sentence(new NetworkNoticeEmailComposer.Line(type, "Léo", 1, null), false))
                .startsWith("Léo et 1 autre ");
        }
        assertThat(NetworkNoticeEmailComposer.sentence(new NetworkNoticeEmailComposer.Line(NetworkNotificationType.REPLY, "Léo", 3, "x"), true))
            .isEqualTo("Léo and 3 others replied to your comment “x”");
        assertThat(NetworkNoticeEmailComposer.sentence(new NetworkNoticeEmailComposer.Line(NetworkNotificationType.MENTION, "Léo", 0, null), false))
            .isEqualTo("Léo vous a mentionné");
        // An idea moving on names no member: the administrators answer for Indezy.
        assertThat(NetworkNoticeEmailComposer.sentence(new NetworkNoticeEmailComposer.Line(NetworkNotificationType.IDEA_STATUS, "Léo", 0, "Thème sombre"), false))
            .isEqualTo("Une idée que vous soutenez a avancé “Thème sombre”");
        assertThat(NetworkNoticeEmailComposer.sentence(new NetworkNoticeEmailComposer.Line(NetworkNotificationType.IDEA_STATUS, "Léo", 0, null), true))
            .isEqualTo("An idea you voted for moved on");
        Email english = composer.compose(new NetworkNoticeEmailComposer.Notices(null, "x@example.com", DocumentLanguage.EN, "tok",
            List.of(new NetworkNoticeEmailComposer.Line(NetworkNotificationType.REPOST, "Léo", 1, null)), "k"));
        assertThat(english.text()).startsWith("Hello,").contains("Léo and 1 other reposted your post");
    }
}
