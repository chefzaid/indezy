package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.CompanyDirectory;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * The verified badge over HTTP: Alice's billing SIRET must be an active establishment the registry names her for; the
 * badge lapses once her billing identity holds another SIRET.
 */
class NetworkVerificationIntegrationTest extends TwoAccountsIntegrationTest {

    private static final String SIRET = "55210055400012";
    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @MockitoBean
    private CompanyDirectory directory;

    @AfterEach
    void closeTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, false);
    }

    private Map<String, Object> card() {
        return call(alice, HttpMethod.GET, "/network/membership", null, Map.class).getBody();
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifiesAMemberTheRegistryNamesForTheirSiret() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        assertThat(call(alice, HttpMethod.POST, "/network/membership/verification", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        String handle = "alice_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(alice, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        // No SIRET yet.
        assertThat(call(alice, HttpMethod.POST, "/network/membership/verification", null, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        call(alice, HttpMethod.PUT, "/users/billing", Map.of("legalName", "Alice Test", "legalForm", "MICRO_ENTREPRISE", "siret", SIRET,
            "vatExempt", true, "address", "18 rue du Sentier\n75002 Paris", "iban", "FR7630006000011234567890189"), Map.class);

        when(directory.registration(SIRET)).thenReturn(Optional.empty());
        assertThat(call(alice, HttpMethod.POST, "/network/membership/verification", null, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        when(directory.registration(SIRET)).thenReturn(Optional.of(new CompanyDirectory.Registration("552100554", true, true,
            List.of("JEAN DUPONT"))));
        assertThat(call(alice, HttpMethod.POST, "/network/membership/verification", null, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        when(directory.registration(SIRET)).thenThrow(new CompanyDirectory.UnavailableException(new java.io.IOException("down")));
        assertThat(call(alice, HttpMethod.POST, "/network/membership/verification", null, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat((Map<String, Object>) card().get("card")).containsEntry("verified", false);

        doReturn(Optional.of(new CompanyDirectory.Registration("552100554", true, true, List.of("Jean Dupont", "ALICE MARIE TEST"))))
            .when(directory).registration(SIRET);
        assertThat((Map<String, Object>) call(alice, HttpMethod.POST, "/network/membership/verification", null, Map.class).getBody()
            .get("card")).containsEntry("verified", true);

        // Another SIRET on the billing identity: the badge lapses until verified again.
        call(alice, HttpMethod.PUT, "/users/billing", Map.of("legalName", "Alice Test", "legalForm", "MICRO_ENTREPRISE",
            "siret", "73282932000074", "vatExempt", true, "address", "18 rue du Sentier\n75002 Paris", "iban", "FR7630006000011234567890189"),
            Map.class);
        assertThat((Map<String, Object>) card().get("card")).containsEntry("verified", false);
    }
}
