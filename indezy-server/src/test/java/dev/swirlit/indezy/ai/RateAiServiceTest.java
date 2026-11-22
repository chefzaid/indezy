package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.dto.OfferRateMarketDto;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.WorkMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateAiServiceTest {

    @Test
    void keepsTheRateSensibleAndItsRangeAroundIt() {
        RateAiService.Suggestion wild = RateAiService.clean(new RateAiService.Answer(9_000, 50, 0, Arrays.asList("A", null, " ", "B", "C", "D")), 650);
        assertThat(wild.rate()).isEqualTo(3_000);
        assertThat(wild.low()).isEqualTo(100);
        assertThat(wild.high()).isEqualTo(3_000);
        assertThat(wild.reasons()).containsExactly("A", "B", "C");
        assertThat(wild.marketMedian()).isEqualTo(650);

        RateAiService.Suggestion inverted = RateAiService.clean(new RateAiService.Answer(600, 700, 500, null), 0);
        assertThat(inverted.low()).isEqualTo(600);
        assertThat(inverted.high()).isEqualTo(600);
        assertThat(inverted.reasons()).isEmpty();
    }

    @Test
    void tellsTheMissionTheProfileAndTheMarket() {
        User user = new User();
        user.setSkills(new ArrayList<>(List.of("Kafka")));
        user.setTargetDailyRate(680);
        user.setCity("Lyon");
        String context = RateAiService.context(new RateAiService.Mission("Lead Kafka", "Kafka, Java", WorkMode.HYBRID, 8, "Paris", null,
            "Flux temps réel"), user, new OfferRateMarketDto(0, 640, 12, "Kafka", null), DocumentLanguage.EN);
        assertThat(context).contains("Answer in English", "Role: Lead Kafka", "HYBRID, 8 remote days a month", "Duration: unknown",
            "Flux temps réel", "Skills: Kafka", "Target daily rate: 680 EUR", "Lives in: Lyon", "naming Kafka first is 640 EUR over 12 offers");
        assertThat(RateAiService.context(new RateAiService.Mission(null, null, null, null, null, null, null), null, null, DocumentLanguage.FR))
            .contains("Answer in French", "Work mode: unknown").doesNotContain("<profile>", "Market:");
    }
}
