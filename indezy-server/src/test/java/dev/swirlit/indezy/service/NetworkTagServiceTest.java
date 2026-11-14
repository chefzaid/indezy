package dev.swirlit.indezy.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkTagServiceTest {

    @Test
    void keepsATagAsPostsDoAndRefusesWhatIsNotOne() {
        assertThat(NetworkTagService.normalized(" #Kafka ")).isEqualTo("kafka");
        assertThat(NetworkTagService.normalized("sap-fico")).isEqualTo("sap-fico");
        assertThat(NetworkTagService.normalized("Écologie")).isEqualTo("écologie");
        for (String wrong : new String[] {null, "", "#", "deux mots", "-dash", "x".repeat(41)}) {
            assertThatThrownBy(() -> NetworkTagService.normalized(wrong)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(NetworkPostService.tags("#Java et #java, #Lyon; mail#pas et ##deux")).containsExactly("java", "lyon");
    }
}
