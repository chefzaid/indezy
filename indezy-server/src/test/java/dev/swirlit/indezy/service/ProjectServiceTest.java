package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Season;
import dev.swirlit.indezy.model.Source;
import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectServiceTest extends ProjectServiceTestSupport {

    @Test
    void findAll_ShouldReturnAllProjects() {
        // Given
        List<Project> projects = Arrays.asList(testProject);
        when(projectRepository.findAll()).thenReturn(projects);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        List<ProjectDto> result = projectService.findAll();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo("Full Stack Developer");
        verify(projectRepository).findAll();
        verify(projectMapper).toDto(testProject);
    }

    @Test
    void findById_WhenProjectExists_ShouldReturnProject() {
        // Given
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        ProjectDto result = projectService.findById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getRole()).isEqualTo("Full Stack Developer");
        verify(projectRepository).findById(1L);
        verify(projectMapper).toDto(testProject);
    }

    @Test
    void findById_WhenProjectNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> projectService.findById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Project not found with id: 1");
        
        verify(projectRepository).findById(1L);
        verify(projectMapper, never()).toDto(any());
    }

    @Test
    void findByFreelanceId_ShouldReturnProjectsForFreelance() {
        // Given
        List<Project> projects = Arrays.asList(testProject);
        when(projectRepository.findByFreelanceId(1L)).thenReturn(projects);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        List<ProjectDto> result = projectService.findByFreelanceId(1L);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFreelanceId()).isEqualTo(1L);
        verify(projectRepository).findByFreelanceId(1L);
        verify(projectMapper).toDto(testProject);
    }

    @Test
    void create_WithValidData_ShouldCreateProject() {
        // Given
        when(projectMapper.toEntity(testProjectDto)).thenReturn(testProject);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        ProjectDto result = projectService.create(testProjectDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRole()).isEqualTo("Full Stack Developer");
        verify(projectMapper).toEntity(testProjectDto);
        verify(freelanceRepository).findById(1L);
        verify(clientRepository).findById(1L);
        verify(projectRepository).save(testProject);
        verify(projectMapper).toDto(testProject);
    }

    @Test
    void create_WithInvalidFreelanceId_ShouldThrowResourceNotFoundException() {
        // Given
        when(projectMapper.toEntity(testProjectDto)).thenReturn(testProject);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> projectService.create(testProjectDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Freelance not found with id: 1");
        
        verify(freelanceRepository).findById(1L);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void update_WithValidData_ShouldUpdateProject() {
        // Given
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        ProjectDto result = projectService.update(1L, testProjectDto);

        // Then
        assertThat(result).isNotNull();
        verify(projectRepository).findById(1L);
        verify(projectMapper).updateEntity(testProjectDto, testProject);
        verify(projectRepository).save(testProject);
        verify(projectMapper).toDto(testProject);
    }

    @Test
    void delete_WhenProjectExists_ShouldDeleteNotesAndActivityReportsAndFreeItsOfferThenProject() {
        // Given
        when(projectRepository.existsById(1L)).thenReturn(true);

        // When
        projectService.delete(1L);

        // Then
        InOrder order = inOrder(projectNoteRepository, activityReportRepository, jobOfferStateRepository, projectRepository);
        order.verify(projectNoteRepository).deleteByProjectId(1L);
        order.verify(activityReportRepository).deleteByProjectId(1L);
        order.verify(jobOfferStateRepository).clearImportedProject(1L);
        order.verify(projectRepository).deleteById(1L);
    }

    @Test
    void delete_WhenProjectNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(projectRepository.existsById(1L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> projectService.delete(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Project not found with id: 1");
        
        verify(projectRepository).existsById(1L);
        verify(projectRepository, never()).deleteById(any());
    }

    @Test
    void findByClientId_ShouldReturnProjectsForClient() {
        // Given
        when(projectRepository.findByClientId(1L)).thenReturn(Arrays.asList(testProject));
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        List<ProjectDto> result = projectService.findByClientId(1L);

        // Then
        assertThat(result).hasSize(1);
        verify(projectRepository).findByClientId(1L);
    }

    @Test
    void create_WithMiddlemanAndSource_ShouldResolveAllRelationships() {
        // Given
        Client middleman = new Client();
        middleman.setId(2L);
        Source source = new Source();
        source.setId(3L);
        testProjectDto.setMiddlemanId(2L);
        testProjectDto.setSourceId(3L);

        when(projectMapper.toEntity(testProjectDto)).thenReturn(testProject);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(clientRepository.findById(2L)).thenReturn(Optional.of(middleman));
        when(sourceRepository.findById(3L)).thenReturn(Optional.of(source));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        ProjectDto result = projectService.create(testProjectDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(testProject.getMiddleman()).isEqualTo(middleman);
        assertThat(testProject.getSource()).isEqualTo(source);
    }

    @Test
    void create_WithoutStatus_ShouldDefaultToContact() {
        // Given
        testProject.setStatus(null);
        when(projectMapper.toEntity(testProjectDto)).thenReturn(testProject);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        projectService.create(testProjectDto);

        // Then
        assertThat(testProject.getStatus()).isEqualTo(ProjectStatus.CONTACT);
    }

    @Test
    void create_WithoutSeason_ShouldJoinTheRunningSeason() {
        Season running = new Season();
        running.setId(4L);
        when(projectMapper.toEntity(testProjectDto)).thenReturn(testProject);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(seasonService.findCurrentSeason(1L)).thenReturn(Optional.of(running));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        projectService.create(testProjectDto);

        assertThat(testProject.getSeason()).isSameAs(running);
    }

    @Test
    void create_WithExplicitSeason_ShouldUseIt() {
        Season chosen = new Season();
        chosen.setId(6L);
        testProjectDto.setSeasonId(6L);
        when(projectMapper.toEntity(testProjectDto)).thenReturn(testProject);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(seasonService.getSeasonOfFreelance(6L, 1L)).thenReturn(chosen);
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        projectService.create(testProjectDto);

        assertThat(testProject.getSeason()).isSameAs(chosen);
        verify(seasonService, never()).findCurrentSeason(any());
    }

    @Test
    void update_WhenProjectNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> projectService.update(999L, testProjectDto))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void update_WithNewMiddlemanAndSource_ShouldReassignRelationships() {
        // Given
        Client middleman = new Client();
        middleman.setId(2L);
        Source source = new Source();
        source.setId(3L);
        testProjectDto.setMiddlemanId(2L);
        testProjectDto.setSourceId(3L);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(clientRepository.findById(2L)).thenReturn(Optional.of(middleman));
        when(sourceRepository.findById(3L)).thenReturn(Optional.of(source));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        projectService.update(1L, testProjectDto);

        // Then
        assertThat(testProject.getMiddleman()).isEqualTo(middleman);
        assertThat(testProject.getSource()).isEqualTo(source);
    }

    @Test
    void update_WithoutMiddlemanAndSource_ShouldClearRelationships() {
        // Given
        Client middleman = new Client();
        middleman.setId(2L);
        Source source = new Source();
        source.setId(3L);
        testProject.setMiddleman(middleman);
        testProject.setSource(source);
        testProjectDto.setMiddlemanId(null);
        testProjectDto.setSourceId(null);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        projectService.update(1L, testProjectDto);

        // Then
        assertThat(testProject.getMiddleman()).isNull();
        assertThat(testProject.getSource()).isNull();
    }

    @Test
    void updateStatus_WhenProjectExists_ShouldUpdateStatus() {
        // Given
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        ProjectDto result = projectService.updateStatus(1L, ProjectStatus.WON, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(testProject.getStatus()).isEqualTo(ProjectStatus.WON);
        assertThat(testProject.getLostReason()).isNull();
        verify(projectRepository).save(testProject);
    }

    @Test
    void updateStatus_WhenLost_ShouldStoreLostReason() {
        // Given
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        projectService.updateStatus(1L, ProjectStatus.LOST, LostReason.RATE_TOO_LOW);

        // Then
        assertThat(testProject.getStatus()).isEqualTo(ProjectStatus.LOST);
        assertThat(testProject.getLostReason()).isEqualTo(LostReason.RATE_TOO_LOW);
    }

    @Test
    void updateStatus_WhenNotLost_ShouldClearLostReason() {
        // Given a project that was previously lost
        testProject.setStatus(ProjectStatus.LOST);
        testProject.setLostReason(LostReason.NO_RESPONSE);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When it moves back into the pipeline
        projectService.updateStatus(1L, ProjectStatus.INTERVIEW, LostReason.NO_RESPONSE);

        // Then the stale reason is cleared
        assertThat(testProject.getStatus()).isEqualTo(ProjectStatus.INTERVIEW);
        assertThat(testProject.getLostReason()).isNull();
    }

    @Test
    void updateStatus_WhenProjectNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> projectService.updateStatus(999L, ProjectStatus.WON, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(projectRepository, never()).save(any());
    }
}
