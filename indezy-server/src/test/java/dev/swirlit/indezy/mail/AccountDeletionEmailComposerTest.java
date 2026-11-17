package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AccountDeletionEmailComposerTest {

    private final AccountDeletionEmailComposer composer = new AccountDeletionEmailComposer(
        new MailProperties("re_test", "Indezy <noreply@swirlit.dev>", "https://indezy.example/", "https://api.resend.test"));

    @Test
    void tellsTheDayTheAccountGoesAndHowToRestoreIt() {
        Email french = composer.compose("Camille", "camille@example.test", DocumentLanguage.FR, LocalDate.of(2026, 12, 17), "key-1");
        assertThat(french.subject()).isEqualTo("Votre compte Indezy sera supprimé le 17 décembre 2026");
        assertThat(french.text()).startsWith("Bonjour Camille,").contains("Restaurer mon compte").contains("https://indezy.example/login");
        assertThat(french.to()).isEqualTo("camille@example.test");
        assertThat(french.idempotencyKey()).isEqualTo("key-1");
        Email english = composer.compose(" ", "camille@example.test", DocumentLanguage.EN, LocalDate.of(2026, 12, 17), "key-2");
        assertThat(english.subject()).isEqualTo("Your Indezy account will be deleted on December 17, 2026");
        assertThat(english.text()).startsWith("Hello,").contains("Restore my account");
        assertThat(english.html()).contains("<a href=\"https://indezy.example/login\">");
    }
}
