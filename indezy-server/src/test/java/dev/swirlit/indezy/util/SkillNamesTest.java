package dev.swirlit.indezy.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillNamesTest {

    @Test
    void givesSpellingsOfOneSkillOneKey() {
        assertThat(List.of("Spring Boot", "SpringBoot", "spring-boot", " SPRING  boot ").stream().map(SkillNames::key).distinct())
            .containsExactly("springboot");
        assertThat(SkillNames.key("Node.js")).isEqualTo(SkillNames.key("NodeJS"));
        assertThat(SkillNames.key("C#")).isNotEqualTo(SkillNames.key("C++"));
        assertThat(SkillNames.key("Éclair")).isEqualTo("eclair");
        assertThat(SkillNames.key(null)).isEmpty();
    }

    @Test
    void namesAKnownSkillByItsUsualNameAndKeepsAnyOtherAsWritten() {
        assertThat(SkillNames.canonical("js")).isEqualTo("JavaScript");
        assertThat(SkillNames.canonical("K8S")).isEqualTo("Kubernetes");
        assertThat(SkillNames.canonical("springboot")).isEqualTo("Spring Boot");
        assertThat(SkillNames.canonical("dot-net")).isEqualTo(".NET");
        assertThat(SkillNames.canonical(" Elixir ")).isEqualTo("Elixir");
        assertThat(SkillNames.same("Postgres", "PostgreSQL")).isTrue();
        assertThat(SkillNames.same("Data Bricks", "databricks")).isTrue();
        assertThat(SkillNames.same("Java", "JavaScript")).isFalse();
        assertThat(SkillNames.same("React", "React Native")).isFalse();
    }

    @Test
    void listsTheWordsAnOfferMayUseForASkill() {
        assertThat(SkillNames.variants("JS")).containsExactly("js", "javascript", "ecmascript", "es6");
        assertThat(SkillNames.variants("Spring-Boot")).containsExactly("spring boot", "springboot");
        assertThat(SkillNames.variants("Elixir")).containsExactly("elixir");
        assertThat(SkillNames.variants("--")).isEmpty();
    }

    @Test
    void neverGivesTwoSkillsTheSameKey() {
        Map<String, String> owners = new HashMap<>();
        SkillNames.aliases().forEach((skill, aliases) -> {
            for (String name : concat(skill, aliases)) {
                String previous = owners.put(SkillNames.key(name), skill);
                assertThat(previous).as(name).isIn(null, skill);
                assertThat(SkillNames.canonical(name)).isEqualTo(skill);
            }
        });
    }

    private static List<String> concat(String skill, List<String> aliases) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(skill), aliases.stream()).toList();
    }
}
