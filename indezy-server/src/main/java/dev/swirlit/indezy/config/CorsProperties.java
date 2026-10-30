package dev.swirlit.indezy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Which browser origins may call the API ({@code cors.*}), with the methods and headers they may send and whether
 * credentials go along. A list left unset is empty.
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins, List<String> allowedMethods, List<String> allowedHeaders,
                             boolean allowCredentials) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        allowedMethods = allowedMethods == null ? List.of() : List.copyOf(allowedMethods);
        allowedHeaders = allowedHeaders == null ? List.of() : List.copyOf(allowedHeaders);
    }
}
