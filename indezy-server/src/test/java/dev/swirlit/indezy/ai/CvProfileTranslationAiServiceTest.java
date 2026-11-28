package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.dto.CvProfileDtos;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CvProfileTranslationAiServiceTest {

    private static final CvProfileDtos.Wording WORDING = new CvProfileDtos.Wording(3L, DocumentLanguage.EN, List.of(
        new CvProfileDtos.Block("headline", "Lead Kafka"), new CvProfileDtos.Block("summary", "Dix ans de Java."),
        new CvProfileDtos.Block("mission-10", "Flux de paie.")));

    @Test
    void sendsEachBlockByItsKeyAndKeepsOnlyTheTranslationsOfTheBlocksGiven() {
        assertThat(CvProfileTranslationAiService.context(WORDING)).startsWith("Translate into English.\n")
            .contains("<block key=\"mission-10\">\nFlux de paie.\n</block>");

        assertThat(CvProfileTranslationAiService.translated(WORDING, new CvProfileTranslationAiService.Translation(List.of(
            new CvProfileTranslationAiService.Block(" summary ", " Ten years of Java. "), new CvProfileTranslationAiService.Block("mission-10", " "),
            new CvProfileTranslationAiService.Block("mission-99", "Made up"))))).containsExactly(java.util.Map.entry("headline", "Lead Kafka"),
            java.util.Map.entry("summary", "Ten years of Java."), java.util.Map.entry("mission-10", "Flux de paie."));
    }
}
