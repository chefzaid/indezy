package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.dto.CompanyHealthDto;
import dev.swirlit.indezy.model.Client;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewAiServiceTest {

    @Test
    void tellsTheCoachWhatTheRegistersSayOfTheCompany() {
        Client client = new Client();
        client.setCompanyName("Banque Exemple");

        assertThat(InterviewAiService.health(client, CompanyHealthDto.UNCHECKED)).isEmpty();
        assertThat(InterviewAiService.health(client, new CompanyHealthDto(true, false, null, null)))
            .contains("Health of Banque Exemple: in business, no insolvency proceedings");
        assertThat(InterviewAiService.health(client, new CompanyHealthDto(true, true, null, null)))
            .contains("Health of Banque Exemple: closed");
        assertThat(InterviewAiService.health(client, new CompanyHealthDto(true, false, CompanyHealthDto.Procedure.REDRESSEMENT,
            LocalDate.of(2026, 3, 2)))).contains("Health of Banque Exemple: under insolvency proceedings (redressement) since 2026-03-02");
    }
}
