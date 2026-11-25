package dev.swirlit.indezy.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InboxWatcherServiceTest {

    @Test
    void readsTheSenderFromTheEnvelopeOrFromTheMessageTheMemberForwardedByHand() {
        assertThat(InboxWatcherService.sender("Léa Martin <Lea@Acme.example>", "", "alice@example.com")).isEqualTo("lea@acme.example");
        String forwarded = """
            À voir.

            ---------- Forwarded message ---------
            De : Léa Martin <lea@acme.example>
            Date: lun. 23 nov. 2026
            Subject: Mission Java""";
        assertThat(InboxWatcherService.sender("Alice <alice@example.com>", forwarded, "alice@example.com")).isEqualTo("lea@acme.example");
        assertThat(InboxWatcherService.sender("alice@example.com", "> From: Alice <alice@example.com>\n> From: paul@esn.example",
            "alice@example.com")).isEqualTo("paul@esn.example");
        assertThat(InboxWatcherService.sender("alice@example.com", "Rien", "alice@example.com")).isNull();
        assertThat(InboxWatcherService.sender(null, null, null)).isNull();
    }

    @Test
    void knowsThePublicMailboxProvidersWhoseDomainSaysNothingOfACompany() {
        assertThat(InboxWatcherService.webmail("gmail.com")).isTrue();
        assertThat(InboxWatcherService.webmail("Outlook.fr")).isTrue();
        assertThat(InboxWatcherService.webmail("yahoo.co.uk")).isTrue();
        assertThat(InboxWatcherService.webmail("acme.example")).isFalse();
        assertThat(InboxWatcherService.webmail("liveperson.com")).isFalse();
    }

    @Test
    void readsACompanysDomainWhateverWayItIsWritten() {
        assertThat(InboxWatcherService.bare("https://www.Acme.example/jobs")).isEqualTo("acme.example");
        assertThat(InboxWatcherService.bare(" acme.example ")).isEqualTo("acme.example");
    }
}
