package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OpportunityMapDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.scraping.GeoLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpportunityMapServiceTest {

    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final GeoLocator geoLocator = mock(GeoLocator.class);
    private final OpportunityMapService service = new OpportunityMapService(freelances, projects, geoLocator);
    private final Freelance freelance = new Freelance();

    private static Project project(long id, String clientCity, String workAddress, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setRole("Role " + id);
        project.setStatus(status);
        project.setWorkAddress(workAddress);
        if (clientCity != null) {
            Client client = new Client();
            client.setCompanyName("Client " + id);
            client.setCity(clientCity);
            project.setClient(client);
        }
        return project;
    }

    @BeforeEach
    void setUp() {
        freelance.setAddress("10 rue de la Paix");
        freelance.setCity("Paris");
        when(freelances.findById(1L)).thenReturn(Optional.of(freelance));
        when(geoLocator.canLocate()).thenReturn(true);
        when(geoLocator.locateAddress("10 rue de la Paix, Paris")).thenReturn(Optional.of(new GeoLocator.Place("Paris", "75", null, 48.869, 2.331)));
        when(geoLocator.locateAddress("Lyon")).thenReturn(Optional.of(new GeoLocator.Place("Lyon", "69", null, 45.764, 4.836)));
        when(geoLocator.locateAddress("92400 Courbevoie")).thenReturn(Optional.of(new GeoLocator.Place("Courbevoie", "92", null, 48.898, 2.257)));
        when(geoLocator.locateAddress("Nowhere")).thenReturn(Optional.empty());
    }

    @Test
    void placesEachOpportunityOnTheBoardAtItsPlaceOfWorkOrItsClient() {
        Project archived = project(5, "Lyon", null, ProjectStatus.LOST);
        archived.setArchivedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        when(projects.findByFreelanceId(1L)).thenReturn(List.of(project(1, "Lyon", null, ProjectStatus.CONTACT),
            project(2, "Lyon", "92400 Courbevoie", ProjectStatus.WON), project(3, "Lyon", null, ProjectStatus.OFFER),
            project(4, "Nowhere", null, ProjectStatus.INTERVIEW), project(6, null, null, ProjectStatus.CONTACT), archived));

        OpportunityMapDto map = service.map(1L);

        assertThat(map.home()).isEqualTo(new OpportunityMapDto.Point(48.869, 2.331));
        assertThat(map.pins()).extracting(OpportunityMapDto.Pin::projectId).containsExactly(1L, 2L, 3L);
        assertThat(map.pins().get(1)).isEqualTo(new OpportunityMapDto.Pin(2L, "Role 2", "Client 2", ProjectStatus.WON, "92400 Courbevoie",
            48.898, 2.257));
        assertThat(map.unplaced()).isEqualTo(2);
        // Two opportunities at Lyon: the address is searched once.
        verify(geoLocator, times(1)).locateAddress("Lyon");
        assertThat(map.toString()).doesNotContain("Lyon");
    }

    @Test
    void placesNothingWithoutTheAddressSearchAndNoHomeWithoutAnAddress() {
        when(geoLocator.canLocate()).thenReturn(false);
        freelance.setAddress(null);
        freelance.setCity(null);
        when(projects.findByFreelanceId(1L)).thenReturn(List.of(project(1, "Lyon", null, ProjectStatus.CONTACT)));

        OpportunityMapDto map = service.map(1L);

        assertThat(map.home()).isNull();
        assertThat(map.pins()).isEmpty();
        assertThat(map.unplaced()).isEqualTo(1);
        verify(geoLocator, never()).locateAddress(any());
        assertThatThrownBy(() -> service.map(9L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
