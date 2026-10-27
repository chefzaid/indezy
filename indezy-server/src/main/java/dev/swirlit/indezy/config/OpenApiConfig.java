package dev.swirlit.indezy.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * The OpenAPI description served at {@code /api/v3/api-docs}: the API's version from the build, a server relative to
 * the page that reads it (the same host in development and in production), and JWT bearer authentication. Error
 * responses and examples are added by {@link OpenApiDocumentation}.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI(ObjectProvider<BuildProperties> build) {
        BuildProperties properties = build.getIfAvailable();
        return new OpenAPI()
                .info(new Info()
                        .title("Indezy API")
                        .description("The API of Indezy, the workspace of French IT freelancers: opportunities and their " +
                            "recruitment steps, clients, ESNs and contacts, the freelance offer feed, activity reports and " +
                            "analytics. Every call but sign-in and the public links needs a bearer token; records belong to " +
                            "the caller's workspace.")
                        .version(properties == null ? "development" : properties.getVersion())
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(new Server().url(contextPath).description("This server")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("The token answered by sign-in (`POST /auth/login`)")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
