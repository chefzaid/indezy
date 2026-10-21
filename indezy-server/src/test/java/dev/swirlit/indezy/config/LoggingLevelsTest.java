package dev.swirlit.indezy.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Production logs the application at INFO: its DEBUG lines, and what they may name, stay in development. */
class LoggingLevelsTest {

    /** How Spring flattens the YAML key {@code "[dev.swirlit.indezy]"} under {@code logging.level}. */
    private static final String APPLICATION_LEVEL = "logging.level[dev.swirlit.indezy]";

    @Test
    void productionLogsTheApplicationAtInfo() throws IOException {
        assertThat(properties("application.yml").get(0).getProperty(APPLICATION_LEVEL)).isEqualTo("INFO");
        assertThat(properties("application-kubernetes.yml")).allSatisfy(source -> assertThat(source.getProperty(APPLICATION_LEVEL)).isNull());
    }

    private static List<PropertySource<?>> properties(String file) throws IOException {
        return new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
    }
}
