package dev.swirlit.indezy.scraping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The place names the first production runs could not locate, on 2026-10-20. */
class PlaceNamesTest {

    @Test
    void leavesArrondissementsCedexAndRemoteWordsAside() {
        assertThat(PlaceNames.commune("Paris 13ème")).isEqualTo("Paris");
        assertThat(PlaceNames.commune("Lyon 3e")).isEqualTo("Lyon");
        assertThat(PlaceNames.commune("marseille cedex 15")).isEqualTo("marseille");
        assertThat(PlaceNames.commune("Nanterre Cedex")).isEqualTo("Nanterre");
        assertThat(PlaceNames.commune("100% Télétravail (Toulouse)")).isEqualTo("Toulouse");
        assertThat(PlaceNames.commune("Courbevoie (92)")).isEqualTo("Courbevoie");
        assertThat(PlaceNames.commune("Full remote")).isNull();
        assertThat(PlaceNames.commune("  ")).isNull();
        assertThat(PlaceNames.commune(null)).isNull();
    }

    @Test
    void keepsTheFirstPlaceOfAList() {
        assertThat(PlaceNames.commune("Lille, Dijon ou Toulouse")).isEqualTo("Lille");
        assertThat(PlaceNames.commune("Malakoff/Corbeil")).isEqualTo("Malakoff");
        assertThat(PlaceNames.commune("Remote / Nantes")).isEqualTo("Nantes");
        assertThat(PlaceNames.commune("Saint-Denis")).isEqualTo("Saint-Denis");
        assertThat(PlaceNames.commune("Aix-en-Provence")).isEqualTo("Aix-en-Provence");
    }

    @Test
    void namesDistrictsAndShortFormsAfterTheirCommune() {
        assertThat(PlaceNames.commune("LA DEFENSE et remote")).isEqualTo("Puteaux");
        assertThat(PlaceNames.commune("SQY")).isEqualTo("Montigny-le-Bretonneux");
        assertThat(PlaceNames.commune("roissy charles de gaulle")).isEqualTo("Roissy-en-France");
        assertThat(PlaceNames.commune("St Herblain")).isEqualTo("Saint-Herblain");
        assertThat(PlaceNames.commune("Ste Geneviève des Bois")).isEqualTo("Sainte-Geneviève des Bois");
        assertThat(PlaceNames.commune("Sophia Antipolis")).isEqualTo("Valbonne");
    }
}
