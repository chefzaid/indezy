package dev.byteworks.indezy.service;

import dev.byteworks.indezy.dto.SourceDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.mapper.SourceMapper;
import dev.byteworks.indezy.model.Freelance;
import dev.byteworks.indezy.model.Source;
import dev.byteworks.indezy.model.enums.EmploymentStatus;
import dev.byteworks.indezy.model.enums.SourceType;
import dev.byteworks.indezy.repository.FreelanceRepository;
import dev.byteworks.indezy.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private FreelanceRepository freelanceRepository;

    @Mock
    private SourceMapper sourceMapper;

    @InjectMocks
    private SourceService sourceService;

    private Source testSource;
    private SourceDto testSourceDto;
    private Freelance testFreelance;

    @BeforeEach
    void setUp() {
        testFreelance = new Freelance();
        testFreelance.setId(1L);
        testFreelance.setFirstName("John");
        testFreelance.setLastName("Doe");
        testFreelance.setEmail("john.doe@example.com");
        testFreelance.setStatus(EmploymentStatus.FREELANCE);

        testSource = new Source();
        testSource.setId(1L);
        testSource.setName("LinkedIn");
        testSource.setType(SourceType.JOB_BOARD);
        testSource.setLink("https://linkedin.com");
        testSource.setIsListing(false);
        testSource.setPopularityRating(4);
        testSource.setUsefulnessRating(5);
        testSource.setNotes("Professional networking platform");
        testSource.setFreelance(testFreelance);

        testSourceDto = new SourceDto();
        testSourceDto.setId(1L);
        testSourceDto.setName("LinkedIn");
        testSourceDto.setType(SourceType.JOB_BOARD);
        testSourceDto.setLink("https://linkedin.com");
        testSourceDto.setIsListing(false);
        testSourceDto.setPopularityRating(4);
        testSourceDto.setUsefulnessRating(5);
        testSourceDto.setNotes("Professional networking platform");
        testSourceDto.setFreelanceId(1L);
    }

    @Test
    void findAll_ShouldReturnAllSources() {
        // Given
        List<Source> sources = Arrays.asList(testSource);
        when(sourceRepository.findAll()).thenReturn(sources);
        when(sourceMapper.toDto(testSource)).thenReturn(testSourceDto);

        // When
        List<SourceDto> result = sourceService.findAll();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("LinkedIn");
        verify(sourceRepository).findAll();
        verify(sourceMapper).toDto(testSource);
    }

    @Test
    void findById_WithExistingId_ShouldReturnSource() {
        // Given
        when(sourceRepository.findById(1L)).thenReturn(Optional.of(testSource));
        when(sourceMapper.toDto(testSource)).thenReturn(testSourceDto);

        // When
        SourceDto result = sourceService.findById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("LinkedIn");
        verify(sourceRepository).findById(1L);
        verify(sourceMapper).toDto(testSource);
    }

    @Test
    void findById_WithNonExistentId_ShouldThrowException() {
        // Given
        when(sourceRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> sourceService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Source not found with id: 999");

        verify(sourceRepository).findById(999L);
        verify(sourceMapper, never()).toDto(any());
    }

    @Test
    void create_WithValidData_ShouldCreateAndReturnSource() {
        // Given
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(sourceMapper.toEntity(testSourceDto)).thenReturn(testSource);
        when(sourceRepository.save(testSource)).thenReturn(testSource);
        when(sourceMapper.toDto(testSource)).thenReturn(testSourceDto);

        // When
        SourceDto result = sourceService.create(testSourceDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("LinkedIn");
        verify(freelanceRepository).findById(1L);
        verify(sourceMapper).toEntity(testSourceDto);
        verify(sourceRepository).save(testSource);
        verify(sourceMapper).toDto(testSource);
    }

    @Test
    void create_WithNonExistentFreelance_ShouldThrowException() {
        // Given
        when(freelanceRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> sourceService.create(testSourceDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Freelance not found with id: 1");

        verify(freelanceRepository).findById(1L);
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void update_WithValidData_ShouldUpdateAndReturnSource() {
        // Given
        when(sourceRepository.findById(1L)).thenReturn(Optional.of(testSource));
        when(sourceRepository.save(testSource)).thenReturn(testSource);
        when(sourceMapper.toDto(testSource)).thenReturn(testSourceDto);

        testSourceDto.setName("Updated LinkedIn");

        // When
        SourceDto result = sourceService.update(1L, testSourceDto);

        // Then
        assertThat(result).isNotNull();
        verify(sourceRepository).findById(1L);
        verify(sourceRepository).save(testSource);
        verify(sourceMapper).toDto(testSource);
    }

    @Test
    void update_WithNonExistentId_ShouldThrowException() {
        // Given
        when(sourceRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> sourceService.update(999L, testSourceDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Source not found with id: 999");

        verify(sourceRepository).findById(999L);
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void delete_WithExistingId_ShouldDeleteSource() {
        // Given
        when(sourceRepository.findById(1L)).thenReturn(Optional.of(testSource));
        doNothing().when(sourceRepository).delete(testSource);

        // When
        sourceService.delete(1L);

        // Then
        verify(sourceRepository).findById(1L);
        verify(sourceRepository).delete(testSource);
    }

    @Test
    void delete_WithNonExistentId_ShouldThrowException() {
        // Given
        when(sourceRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> sourceService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Source not found with id: 999");

        verify(sourceRepository).findById(999L);
        verify(sourceRepository, never()).delete(any());
    }

    @Test
    void findByFreelanceId_ShouldReturnSourceList() {
        // Given
        List<Source> sources = Arrays.asList(testSource);
        when(sourceRepository.findByFreelanceId(1L)).thenReturn(sources);
        when(sourceMapper.toDto(testSource)).thenReturn(testSourceDto);

        // When
        List<SourceDto> result = sourceService.findByFreelanceId(1L);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("LinkedIn");
        verify(sourceRepository).findByFreelanceId(1L);
        verify(sourceMapper).toDto(testSource);
    }

    @Test
    void findByFreelanceIdAndType_ShouldReturnFilteredSources() {
        // Given
        List<Source> sources = Arrays.asList(testSource);
        when(sourceRepository.findByFreelanceIdAndType(1L, SourceType.JOB_BOARD)).thenReturn(sources);
        when(sourceMapper.toDto(testSource)).thenReturn(testSourceDto);

        // When
        List<SourceDto> result = sourceService.findByFreelanceIdAndType(1L, SourceType.JOB_BOARD);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(SourceType.JOB_BOARD);
        verify(sourceRepository).findByFreelanceIdAndType(1L, SourceType.JOB_BOARD);
        verify(sourceMapper).toDto(testSource);
    }

    @Test
    void findByIdWithProjects_ShouldReturnSourceWithProjects() {
        // Given
        when(sourceRepository.findByIdWithProjects(1L)).thenReturn(Optional.of(testSource));
        when(sourceMapper.toDto(testSource)).thenReturn(testSourceDto);

        // When
        SourceDto result = sourceService.findByIdWithProjects(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("LinkedIn");
        verify(sourceRepository).findByIdWithProjects(1L);
        verify(sourceMapper).toDto(testSource);
    }

    @Test
    void getAveragePopularityRating_ShouldReturnAverageRating() {
        // Given
        when(sourceRepository.findAveragePopularityRatingByFreelanceId(1L)).thenReturn(4.2);

        // When
        Double result = sourceService.getAveragePopularityRating(1L);

        // Then
        assertThat(result).isEqualTo(4.2);
        verify(sourceRepository).findAveragePopularityRatingByFreelanceId(1L);
    }

    @Test
    void getAverageUsefulnessRating_ShouldReturnAverageRating() {
        // Given
        when(sourceRepository.findAverageUsefulnessRatingByFreelanceId(1L)).thenReturn(4.5);

        // When
        Double result = sourceService.getAverageUsefulnessRating(1L);

        // Then
        assertThat(result).isEqualTo(4.5);
        verify(sourceRepository).findAverageUsefulnessRatingByFreelanceId(1L);
    }
}
