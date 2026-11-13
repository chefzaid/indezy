package dev.swirlit.indezy.scraping;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebAddressesTest {

    @Test
    void encodesTheCharactersNoUriMayHold() throws Exception {
        // As OuiDesk's sitemap wrote a mission on 2026-11-13: its accents are fine, its quotes are not.
        assertThat(WebAddresses.encoded("https://ouidesk.com/mission/Développeur-Java-Angular-\"H-F\"-e53b18319b90bcf15c113b05d316ff7c"))
            .isEqualTo("https://ouidesk.com/mission/Développeur-Java-Angular-%22H-F%22-e53b18319b90bcf15c113b05d316ff7c");
        assertThat(WebAddresses.encoded("https://www.example.com/offre/data engineer?q={java}|100%"))
            .isEqualTo("https://www.example.com/offre/data%20engineer?q=%7Bjava%7D%7C100%25");
        // Its escapes kept, a non-breaking space encoded in UTF-8.
        assertThat(WebAddresses.encoded("https://www.example.com/caf%C3%A9 \"x\"")).isEqualTo("https://www.example.com/caf%C3%A9%C2%A0%22x%22");
        assertThat(WebAddresses.uri("https://www.example.com/a b").getRawPath()).isEqualTo("/a%20b");
    }

    @Test
    void keepsAValidAddressAsWritten() {
        for (String address : new String[] {
            "https://ouidesk.com/mission/Consultant-réseaux-et-sécurité-H-F-1196ae9a011ad6a2c1d91fdb23574712",
            "https://ouidesk.com/mission/Analyste-Développeur-C-C++-(H-F)-8065e0c61b0eaeda38c703899fc97a42",
            "https://ouidesk.com/mission/Consultant-r%c3%a9seaux-et-s%c3%a9curit%c3%a9-H-F-1196ae9a011ad6a2c1d91fdb23574712/",
            "https://mindquest.io/fr/missions-freelance-offres-emploi-it-finance?categories=Finance"}) {
            assertThat(WebAddresses.encoded(address)).as(address).isEqualTo(address);
        }
    }

    @Test
    void reportsAnAddressNoRequestCanBeMadeOf() {
        assertThatThrownBy(() -> WebAddresses.uri("://ouidesk.com/mission/x")).isInstanceOf(MalformedURLException.class)
            .hasMessageContaining("scheme name");
    }
}
