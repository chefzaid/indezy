package dev.swirlit.indezy.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.swirlit.indezy.service.NetworkReviewFilter.Reason;
import static org.assertj.core.api.Assertions.assertThat;

class NetworkReviewFilterTest {

    private static final List<String> NONE = List.of();

    @Test
    void letsAFairReviewThrough() {
        assertThat(NetworkReviewFilter.reasons("Paid at 45 days, a quick process, and the rate of 650 € a day was the one signed in 2026.",
            List.of("Jean Dupontel", "Dupontel"), NONE)).isEmpty();
    }

    @Test
    void holdsPersonalData() {
        assertThat(NetworkReviewFilter.reasons("Write to jean.d@acme.fr or call +33 6 12 34 56 78", NONE, NONE))
            .containsExactlyInAnyOrder(Reason.EMAIL, Reason.PHONE);
        assertThat(NetworkReviewFilter.reasons("Ask for 0612345678", NONE, NONE)).containsExactly(Reason.PHONE);
        assertThat(NetworkReviewFilter.reasons("Le manager, Mme Dupontél, change d'avis.", List.of("Marie Dupontel", "Dupontel"), NONE))
            .containsExactly(Reason.CONTACT_NAME);
        // A short last name, or one inside another word, is not a name given.
        assertThat(NetworkReviewFilter.reasons("Leo was nice to work with in general.", List.of("Le"), NONE)).isEmpty();
        assertThat(NetworkReviewFilter.reasons("See www.example.com for their side.", NONE, NONE)).containsExactly(Reason.LINKS);
    }

    @Test
    void holdsInsultsAccusationsRepeatsAndShouting() {
        assertThat(NetworkReviewFilter.reasons("Des connards, à fuir.", NONE, NONE)).containsExactly(Reason.INSULT);
        assertThat(NetworkReviewFilter.reasons("Une vraie arnaque, des escrocs.", NONE, NONE)).containsExactly(Reason.ACCUSATION);
        assertThat(NetworkReviewFilter.reasons("Slow to pay.", NONE, List.of("slow to pay"))).containsExactly(Reason.REPEATED);
        assertThat(NetworkReviewFilter.reasons("THEY NEVER ANSWER ANY EMAIL AND PAY VERY LATE", NONE, NONE)).containsExactly(Reason.SHOUTING);
    }
}
