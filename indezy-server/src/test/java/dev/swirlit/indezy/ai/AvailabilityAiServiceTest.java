package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityAiServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    private static Project mission(ProjectStatus status, LocalDate start, int months) {
        Project project = new Project();
        project.setStatus(status);
        project.setStartDate(start);
        project.setDurationInMonths(months);
        return project;
    }

    @Test
    void announcesTheDayAfterTheRunningMissionEnds() {
        // Ends on 2027-01-31: available on 2027-02-01; a lost one and a past one do not count.
        assertThat(AvailabilityAiService.availableFrom(List.of(mission(ProjectStatus.WON, LocalDate.of(2026, 11, 1), 3),
            mission(ProjectStatus.LOST, LocalDate.of(2026, 11, 1), 12), mission(ProjectStatus.WON, LocalDate.of(2025, 1, 1), 2)),
            LocalDate.of(2027, 6, 1), TODAY)).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(AvailabilityAiService.availableFrom(List.of(), LocalDate.of(2027, 3, 1), TODAY)).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(AvailabilityAiService.availableFrom(List.of(), LocalDate.of(2026, 1, 1), TODAY)).isEqualTo(TODAY);
        assertThat(AvailabilityAiService.availableFrom(List.of(), null, TODAY)).isEqualTo(TODAY);
    }

    @Test
    void tellsTheProfile() {
        User user = new User();
        user.setSkills(new ArrayList<>(List.of("Java")));
        user.setPosition("Architecte Java");
        user.setCity("Lyon");
        assertThat(AvailabilityAiService.context(user, LocalDate.of(2027, 2, 1), DocumentLanguage.EN)).contains("Answer in English",
            "Available from: 2027-02-01", "Position: Architecte Java", "Skills: Java", "Based in: Lyon");
    }
}
