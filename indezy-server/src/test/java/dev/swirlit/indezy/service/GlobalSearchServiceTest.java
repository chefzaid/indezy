package dev.swirlit.indezy.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSearchServiceTest {

    @Test
    void foldsTheWordsTypedLongestFirst() {
        assertThat(GlobalSearchService.words("  Développeur   JAVA  java ")).containsExactly("developpeur", "java");
        assertThat(GlobalSearchService.words(null)).isEmpty();
    }

    @Test
    void needsEveryWordInOneField() {
        assertThat(GlobalSearchService.containsAll("Développeur Java senior", List.of("java", "developpeur"))).isTrue();
        assertThat(GlobalSearchService.containsAll("Développeur Kotlin", List.of("java", "developpeur"))).isFalse();
        assertThat(GlobalSearchService.containsAll(null, List.of("java"))).isFalse();
    }

    @Test
    void cutsAPassageAroundTheWordAtSpaces() {
        String text = "Premier échange avec le client.\n\nIl cherche un profil " + "très ".repeat(20) +
            "expérimenté sur Kafka et le streaming, pour une mission longue " + "avec beaucoup de détails ".repeat(10);
        String excerpt = GlobalSearchService.excerpt(text, "kafka");

        assertThat(excerpt).startsWith("…").endsWith("…").contains("Kafka").doesNotContain("\n");
        assertThat(excerpt.length()).isLessThanOrEqualTo(142);
        assertThat(GlobalSearchService.excerpt("Court texte", "texte")).isEqualTo("Court texte");
        assertThat(GlobalSearchService.excerpt("Ville: Saint-Étienne", "etienne")).isEqualTo("Ville: Saint-Étienne");
    }
}
