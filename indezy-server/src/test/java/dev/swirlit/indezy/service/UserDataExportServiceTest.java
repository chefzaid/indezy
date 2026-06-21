package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDataExportServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private ProjectService projectService;
    @Mock
    private ClientService clientService;
    @Mock
    private ContactService contactService;
    @Mock
    private SourceService sourceService;

    @InjectMocks
    private UserDataExportService userDataExportService;

    @Test
    void exportUserData_ShouldSerializeProfileAndOwnedEntities() {
        UserDto profile = new UserDto();
        profile.setFirstName("John");
        profile.setEmail("john.doe@example.com");
        when(userService.getUserProfile(1L)).thenReturn(profile);

        ProjectDto project = new ProjectDto();
        project.setRole("Backend Dev");
        when(projectService.findByFreelanceId(1L)).thenReturn(List.of(project));
        when(clientService.findByFreelanceId(1L)).thenReturn(List.of());
        when(contactService.findByFreelanceId(1L)).thenReturn(List.of());
        when(sourceService.findByFreelanceId(1L)).thenReturn(List.of());

        String json = new String(userDataExportService.exportUserData(1L), StandardCharsets.UTF_8);

        // Every section is present, and the profile and projects carry their data.
        assertThat(json)
            .contains("\"profile\"")
            .contains("John")
            .contains("john.doe@example.com")
            .contains("\"projects\"")
            .contains("Backend Dev")
            .contains("\"clients\"")
            .contains("\"contacts\"")
            .contains("\"sources\"");
    }
}
