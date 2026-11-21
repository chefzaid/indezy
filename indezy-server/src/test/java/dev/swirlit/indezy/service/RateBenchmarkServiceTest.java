package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.SkillDetail;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.RateRegion;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateBenchmarkServiceTest {

    @Test
    void placesAMissionByItsWorkModeAndPostalCode() {
        Project project = new Project();
        project.setWorkMode(WorkMode.REMOTE);
        assertThat(RateBenchmarkService.region(project)).isEqualTo(RateRegion.REMOTE);
        project.setWorkMode(WorkMode.HYBRID);
        project.setWorkAddress("Tour First, 92400 Courbevoie");
        assertThat(RateBenchmarkService.region(project)).isEqualTo(RateRegion.ILE_DE_FRANCE);
        project.setWorkAddress("12 quai de Saône 69002 Lyon");
        assertThat(RateBenchmarkService.region(project)).isEqualTo(RateRegion.REGIONS);
        project.setWorkAddress(null);
        Client client = new Client();
        client.setCity("Paris");
        project.setClient(client);
        assertThat(RateBenchmarkService.region(project)).isEqualTo(RateRegion.ILE_DE_FRANCE);
        client.setCity("Nantes");
        assertThat(RateBenchmarkService.region(project)).isNull();
    }

    @Test
    void readsTheSeniorityOnASkillFromItsYears() {
        User user = new User();
        SkillDetail java = new SkillDetail();
        java.setYears(9);
        SkillDetail go = new SkillDetail();
        go.setYears(1);
        user.setSkills(List.of("Java", "Go"));
        user.setSkillDetails(Map.of("Java", java, "Go", go));
        assertThat(RateBenchmarkService.seniority(user, "java")).isEqualTo(Seniority.SENIOR);
        assertThat(RateBenchmarkService.seniority(user, "Go")).isEqualTo(Seniority.JUNIOR);
        assertThat(RateBenchmarkService.seniority(user, "Rust")).isNull();
    }

    @Test
    void takesTheNearestRankPercentiles() {
        List<Integer> rates = List.of(500, 600, 650, 700, 800);
        assertThat(RateBenchmarkService.percentile(rates, 0.5)).isEqualTo(650);
        assertThat(RateBenchmarkService.percentile(rates, 0.25)).isEqualTo(600);
        assertThat(RateBenchmarkService.percentile(rates, 0.75)).isEqualTo(700);
    }
}
