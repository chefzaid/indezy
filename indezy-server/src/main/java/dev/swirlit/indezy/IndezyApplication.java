package dev.swirlit.indezy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.SQLException;

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties
public class IndezyApplication {

    public static void main(String[] args) throws SQLException {
        if (!Boolean.parseBoolean(System.getenv("INDEZY_SCHEMA_MIGRATION_ONLY"))) {
            SpringApplication.run(IndezyApplication.class, args);
            return;
        }

        var database = new DriverManagerDataSource(
            requiredEnvironment("SPRING_DATASOURCE_URL"),
            requiredEnvironment("SPRING_DATASOURCE_USERNAME"),
            requiredEnvironment("SPRING_DATASOURCE_PASSWORD"));
        SchemaMigration.run(database, connection -> {
            var application = new SpringApplication(IndezyApplication.class);
            application.setAdditionalProfiles("schema-migration");
            application.addInitializers(context -> context.getBeanFactory().registerSingleton(
                "dataSource", new SingleConnectionDataSource(connection, true)));
            // Hibernate initializes inside run(): acquire the database lock first.
            // DDL uses that same connection, so reconnecting cannot bypass a lost lock.
            try (var context = application.run(args)) {
                if (!context.isActive()) {
                    throw new IllegalStateException("Schema migration context did not start");
                }
            }
        });
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Schema migration requires " + name);
        }
        return value;
    }
}
