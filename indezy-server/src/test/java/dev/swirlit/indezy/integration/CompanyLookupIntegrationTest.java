package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The company lookup answers signed-in accounts only, and nothing when the directory cannot be reached. */
class CompanyLookupIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    void answersSignedInAccountsOnly() {
        assertThat(exchange("/companies/search?q=acme", HttpMethod.GET, HttpEntity.EMPTY, String.class).getStatusCode())
            .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        // The test directory is a closed port: the lookup finds nothing rather than failing the request.
        assertThat(call(alice, HttpMethod.GET, "/companies/search?q=acme", null, List.class).getBody()).isEmpty();
        assertThat(call(alice, HttpMethod.GET, "/companies/search?q=ab", null, List.class).getBody()).isEmpty();
    }
}
