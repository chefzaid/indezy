package dev.swirlit.indezy.service;

import dev.swirlit.indezy.service.NetworkSpamFilter.Reason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkSpamFilterTest {

    @Test
    void scoresLinksRepeatsMentionsAndShouting() {
        assertThat(NetworkSpamFilter.score("Un retour sur Kafka en production", false, List.of())).isEmpty();
        assertThat(NetworkSpamFilter.score("https://a.example www.b.example http://c.example/x", false, List.of()))
            .containsExactly(Reason.LINKS);
        assertThat(NetworkSpamFilter.score("Voir https://a.example", true, List.of())).containsExactly(Reason.NEW_MEMBER_LINK);
        assertThat(NetworkSpamFilter.score("Voir https://a.example", false, List.of())).isEmpty();
        assertThat(NetworkSpamFilter.score("  Achetez   MES formations ", false, List.of("achetez mes formations")))
            .containsExactly(Reason.REPEATED);
        assertThat(NetworkSpamFilter.score("@leo_m @ines_b @paul_d @anna_k @marc_t merci", false, List.of()))
            .containsExactly(Reason.MENTIONS);
        assertThat(NetworkSpamFilter.score("@leo_m @ines_b @paul_d @anna_k merci", false, List.of())).isEmpty();
        assertThat(NetworkSpamFilter.score("ACHETEZ MAINTENANT LA MEILLEURE FORMATION JAVA", false, List.of()))
            .containsExactly(Reason.SHOUTING);
        assertThat(NetworkSpamFilter.score("JAVA KAFKA", false, List.of())).isEmpty();
        assertThat(Reason.LINKS.points() + Reason.NEW_MEMBER_LINK.points()).isEqualTo(NetworkSpamFilter.HOLD_AT);
        assertThat(Reason.REPEATED.points()).isEqualTo(NetworkSpamFilter.HOLD_AT);
    }
}
