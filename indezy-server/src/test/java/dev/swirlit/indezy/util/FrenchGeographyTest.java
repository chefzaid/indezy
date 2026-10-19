package dev.swirlit.indezy.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrenchGeographyTest {

    @Test
    void findsTheDepartmentOfAPostalCode() {
        assertThat(FrenchGeography.departmentOfPostalCode("75008")).get().extracting(FrenchGeography.Department::name).isEqualTo("Paris");
        assertThat(FrenchGeography.departmentOfPostalCode(" 92400 ")).get().extracting(FrenchGeography.Department::code).isEqualTo("92");
        assertThat(FrenchGeography.departmentOfPostalCode("20000")).get().extracting(FrenchGeography.Department::code).isEqualTo("2A");
        assertThat(FrenchGeography.departmentOfPostalCode("20200")).get().extracting(FrenchGeography.Department::code).isEqualTo("2B");
        assertThat(FrenchGeography.departmentOfPostalCode("97400")).get().extracting(FrenchGeography.Department::name).isEqualTo("La Réunion");
        assertThat(FrenchGeography.departmentOfPostalCode("98000")).isEmpty();
        assertThat(FrenchGeography.departmentOfPostalCode("7500")).isEmpty();
        assertThat(FrenchGeography.departmentOfPostalCode(null)).isEmpty();
    }

    @Test
    void findsDepartmentsAndRegionsByName() {
        assertThat(FrenchGeography.departmentNamed("hauts de seine")).get().extracting(FrenchGeography.Department::regionCode).isEqualTo("11");
        assertThat(FrenchGeography.departmentNamed("Côte-d'Or")).get().extracting(FrenchGeography.Department::code).isEqualTo("21");
        assertThat(FrenchGeography.departmentNamed("Atlantis")).isEmpty();
        assertThat(FrenchGeography.departmentNamed(null)).isEmpty();
        assertThat(FrenchGeography.regionNamed("ile-de-france")).get().extracting(FrenchGeography.Region::code).isEqualTo("11");
        assertThat(FrenchGeography.regionNamed("Provence-Alpes-Côte d'Azur")).get().extracting(FrenchGeography.Region::code).isEqualTo("93");
        assertThat(FrenchGeography.region("84")).get().extracting(FrenchGeography.Region::name).isEqualTo("Auvergne-Rhône-Alpes");
        assertThat(FrenchGeography.department(null)).isEmpty();
    }

    @Test
    void listsRegionsByNameAndTheirDepartmentsByCode() {
        assertThat(FrenchGeography.regions()).hasSize(18).first().extracting(FrenchGeography.Region::name).isEqualTo("Auvergne-Rhône-Alpes");
        assertThat(FrenchGeography.departmentsOf("11")).extracting(FrenchGeography.Department::code)
            .containsExactly("75", "77", "78", "91", "92", "93", "94", "95");
        assertThat(FrenchGeography.departmentsOf("94")).extracting(FrenchGeography.Department::code).containsExactly("2A", "2B");
    }

    @Test
    void foldsTextForMatching() {
        assertThat(TextFolding.fold("Île-de-FRANCE")).isEqualTo("ile-de-france");
        assertThat(TextFolding.words("  Développeur C++ / .NET, Senior ")).isEqualTo("developpeur c++ .net senior");
        assertThat(TextFolding.fold(null)).isNull();
        assertThat(TextFolding.words(null)).isNull();
    }
}
