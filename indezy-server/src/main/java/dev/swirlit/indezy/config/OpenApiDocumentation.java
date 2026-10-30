package dev.swirlit.indezy.config;

import dev.swirlit.indezy.exception.ErrorResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Completes the generated OpenAPI description: the error responses each operation can answer, in the
 * {@code ErrorResponse} shape the API's exception handler writes, and an example body for the main schemas, read from
 * {@code openapi/examples/<Schema>.json}.
 */
@Component
public final class OpenApiDocumentation implements OpenApiCustomizer {

    static final String EXAMPLES = "classpath:openapi/examples/*.json";
    static final String ERROR_SCHEMA = "ErrorResponse";

    /** Paths reached without a token: signing in and registering, and the links sent by email. */
    private static final List<String> PUBLIC_PREFIXES = List.of("/auth/", "/public/");

    private final Map<String, Object> examples;

    public OpenApiDocumentation(JsonMapper jsonMapper) {
        this.examples = readExamples(jsonMapper);
    }

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        // The body of every error answer, which no operation returns as such.
        ModelConverters.getInstance().readAll(new AnnotatedType(ErrorResponse.class))
            .forEach(openApi.getComponents()::addSchemas);
        if (openApi.getComponents().getSchemas() != null) {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            examples.forEach((name, example) -> {
                Schema<?> schema = schemas.get(name);
                if (schema != null) {
                    schema.setExample(example);
                }
            });
        }
        if (openApi.getPaths() != null) {
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap()
                .forEach((method, operation) -> addErrors(path, method, operation)));
        }
    }

    /** The example bodies by schema name. */
    Map<String, Object> examples() {
        return examples;
    }

    private void addErrors(String path, PathItem.HttpMethod method, Operation operation) {
        ApiResponses responses = Objects.requireNonNullElseGet(operation.getResponses(), ApiResponses::new);
        operation.setResponses(responses);
        List<Parameter> parameters = Objects.requireNonNullElse(operation.getParameters(), List.of());
        boolean takesInput = operation.getRequestBody() != null ||
            parameters.stream().anyMatch(parameter -> !"path".equals(parameter.getIn()));
        if (takesInput || path.contains("{")) {
            add(responses, "400", "Invalid input: a missing or malformed field or parameter",
                error(400, "Bad Request", "Validation failed", path));
        }
        if (PUBLIC_PREFIXES.stream().noneMatch(path::startsWith)) {
            add(responses, "401", "No valid bearer token", error(401, "Unauthorized", "Authentication required", path));
            add(responses, "403", "The workspace or record belongs to another account",
                error(403, "Forbidden", "Access denied to resource", path));
        }
        if (path.contains("{")) {
            add(responses, "404", "No such record in the caller's workspace", error(404, "Not Found", "Record not found", path));
        }
        if (method == PathItem.HttpMethod.POST || method == PathItem.HttpMethod.PUT || method == PathItem.HttpMethod.PATCH) {
            add(responses, "409", "The change conflicts with the workspace's state, such as a name already taken",
                error(409, "Conflict", "A template is already named Usual", path));
        }
    }

    private static void add(ApiResponses responses, String code, String description, Map<String, Object> example) {
        if (responses.containsKey(code)) {
            return;
        }
        Schema<?> schema = new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA);
        responses.addApiResponse(code, new ApiResponse().description(description)
            .content(new Content().addMediaType("application/json", new MediaType().schema(schema).example(example))));
    }

    private static Map<String, Object> error(int status, String error, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", "2026-10-27T09:30:00");
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("path", "/api" + path);
        return body;
    }

    private static Map<String, Object> readExamples(JsonMapper reader) {
        Map<String, Object> read = new LinkedHashMap<>();
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources(EXAMPLES)) {
                String name = Objects.requireNonNull(resource.getFilename()).replaceFirst("\\.json$", "");
                try (InputStream input = resource.getInputStream()) {
                    read.put(name, reader.readValue(input, Object.class));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the OpenAPI examples", e);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not read the OpenAPI examples", e);
        }
        return read;
    }
}
