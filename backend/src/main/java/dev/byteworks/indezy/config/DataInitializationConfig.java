package dev.byteworks.indezy.config;

import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Configuration class for data initialization in development environments.
 * This ensures sample data is only loaded in dev and devcontainer profiles.
 */
@Configuration
@Slf4j
public class DataInitializationConfig {

    /**
     * CommandLineRunner that logs when sample data initialization is active.
     * Only runs in dev and devcontainer profiles.
     */
    @Bean
    @Profile({"dev", "devcontainer", "local"})
    public CommandLineRunner dataInitializer(Environment environment) {
        return args -> {
            String[] activeProfiles = environment.getActiveProfiles();
            log.info("=== DEVELOPMENT MODE ACTIVE ===");
            log.info("Active profiles: {}", String.join(", ", activeProfiles));
            log.info("Sample data initialization enabled for development");
            if (Arrays.asList(activeProfiles).contains("local")) {
                log.info("Database will be populated with sample data from data-local.sql (H2)");
            } else {
                log.info("Database will be populated with sample data from data-dev.sql (PostgreSQL)");
            }
            log.info("===============================");
        };
    }

    /**
     * CommandLineRunner that warns when running in production mode.
     * Only runs when neither dev nor devcontainer profiles are active.
     */
    @Bean
    @Profile("!dev & !devcontainer & !local")
    public CommandLineRunner productionWarning(Environment environment) {
        return args -> {
            String[] activeProfiles = environment.getActiveProfiles();
            log.info("=== PRODUCTION MODE ACTIVE ===");
            log.info("Active profiles: {}", activeProfiles.length > 0 ? String.join(", ", activeProfiles) : "default");
            log.info("Sample data initialization DISABLED");
            log.info("Database will NOT be populated with sample data");
            log.info("===============================");
        };
    }
}
