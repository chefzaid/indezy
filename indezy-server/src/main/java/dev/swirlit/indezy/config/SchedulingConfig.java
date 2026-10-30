package dev.swirlit.indezy.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Runs the background jobs ({@code @Scheduled}: offer collection, webhook deliveries, nightly clean-ups, digests and
 * emails) unless {@code indezy.scheduling-enabled} is false, as in the one-off seed and schema migration runs and in tests.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "indezy.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
