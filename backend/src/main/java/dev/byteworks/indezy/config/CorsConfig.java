package dev.byteworks.indezy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "cors")
public class CorsConfig {

    private List<String> allowedOrigins;
    private List<String> allowedMethods;
    private List<String> allowedHeaders;
    private boolean allowCredentials;

    // Getters and setters
    public List<String> getAllowedOrigins() {
        return allowedOrigins != null ? new ArrayList<>(allowedOrigins) : new ArrayList<>();
    }

    public void setAllowedOrigins(final List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins != null ? new ArrayList<>(allowedOrigins) : new ArrayList<>();
    }

    public List<String> getAllowedMethods() {
        return allowedMethods != null ? new ArrayList<>(allowedMethods) : new ArrayList<>();
    }

    public void setAllowedMethods(final List<String> allowedMethods) {
        this.allowedMethods = allowedMethods != null ? new ArrayList<>(allowedMethods) : new ArrayList<>();
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders != null ? new ArrayList<>(allowedHeaders) : new ArrayList<>();
    }

    public void setAllowedHeaders(final List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders != null ? new ArrayList<>(allowedHeaders) : new ArrayList<>();
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Set allowed origins
        configuration.setAllowedOrigins(getAllowedOrigins());
        // Set allowed methods
        configuration.setAllowedMethods(getAllowedMethods());
        // Set allowed headers
        configuration.setAllowedHeaders(getAllowedHeaders());

        // Always allow common headers explicitly
        configuration.addAllowedHeader("Authorization");
        configuration.addAllowedHeader("Content-Type");
        configuration.addAllowedHeader("Accept");
        configuration.addAllowedHeader("X-Requested-With");
        configuration.addAllowedHeader("Origin");

        // Set credentials
        configuration.setAllowCredentials(isAllowCredentials());

        // Expose headers that the client can access
        configuration.addExposedHeader("Authorization");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
