package dev.swirlit.indezy.exception;

import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lets Spring MVC raise its own request errors on a probe controller and pick the {@link GlobalExceptionHandler} method
 * for each, so the dedicated handlers keep winning over the generic one, which keeps the status of the others.
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerMvcTest {

    private static final String REJECTED_EMAIL = "jane.private@mail.example";

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void missingHeader_ShouldAnswerBadRequestAndLogOnlyTheType(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/probe/header"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("Bad Request")))
            .andExpect(jsonPath("$.message", is("Required header 'X-Request-Id' is not present.")));

        assertThat(handlerLines(output)).singleElement().asString()
            .contains("WARN")
            .endsWith("Rejected request: MissingRequestHeaderException (400)");
    }

    @Test
    void parameterConstraint_ShouldAnswerAValidationErrorWithoutTheRejectedValue(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/probe/search").param("q", REJECTED_EMAIL))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("Validation Failed")))
            .andExpect(jsonPath("$.validationErrors.q", is("size must be between 0 and 10")))
            .andExpect(content().string(not(containsString(REJECTED_EMAIL))));

        assertThat(handlerLines(output)).singleElement().asString()
            .contains("WARN")
            .endsWith("Validation failed for ProbeController.search: q (Size)");
    }

    @Test
    void missingParameter_ShouldStillUseTheMalformedRequestHandler(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/probe/search"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", is("The request is malformed or has invalid parameters")));

        assertThat(handlerLines(output)).singleElement().asString().endsWith("Rejected malformed request: parameter 'q' is missing");
    }

    @Test
    void methodNotAllowed_ShouldListTheSupportedMethodsInTheAllowHeader(CapturedOutput output) throws Exception {
        mockMvc.perform(delete("/probe/search"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string(HttpHeaders.ALLOW, "GET"))
            .andExpect(jsonPath("$.error", is("Method Not Allowed")));

        assertThat(handlerLines(output)).singleElement().asString().contains("WARN", "Method not supported");
    }

    /**
     * Only the lines of the handler's logger, whose name ends the logger column in both the Spring Boot and the default
     * Logback pattern: the test profile logs Spring MVC at DEBUG, which names the handler method and quotes the messages.
     */
    private static List<String> handlerLines(CapturedOutput output) {
        return output.getAll().lines().filter(line -> line.contains("exception.GlobalExceptionHandler ")).toList();
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @GetMapping("/header")
        public String header(@RequestHeader("X-Request-Id") String requestId) {
            return requestId;
        }

        @GetMapping("/search")
        public String search(@RequestParam @Size(max = 10) String q) {
            return q;
        }
    }
}
