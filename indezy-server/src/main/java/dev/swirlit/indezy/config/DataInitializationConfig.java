package dev.swirlit.indezy.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Reports whether the explicit database seed profile is active.
 */
@Configuration
@Slf4j
public class DataInitializationConfig {

    /**
     * Log the destructive sample-data initialization started by {@code mask db-reset}.
     */
    @Bean
    @Profile("seed")
    public CommandLineRunner dataInitializer(Environment environment) {
        return args -> {
            String[] activeProfiles = environment.getActiveProfiles();
            log.info("=== DATABASE SEED MODE ACTIVE ===");
            log.info("Active profiles: {}", String.join(", ", activeProfiles));
            log.info("PostgreSQL will be recreated and populated from data-dev.sql");
            log.info("===============================");
        };
    }

    /**
     * Confirm that normal application startup preserves the current database.
     */
    @Bean
    @Profile("!seed")
    public CommandLineRunner normalStartup(Environment environment) {
        return args -> {
            String[] activeProfiles = environment.getActiveProfiles();
            log.info("=== DATABASE SEED MODE DISABLED ===");
            log.info("Active profiles: {}", activeProfiles.length > 0 ? String.join(", ", activeProfiles) : "default");
            log.info("Existing data will be preserved; use 'mask db-reset' to load sample data");
            log.info("===============================");
        };
    }
}
