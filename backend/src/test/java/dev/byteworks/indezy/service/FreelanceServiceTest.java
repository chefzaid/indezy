package dev.byteworks.indezy.service;

import dev.byteworks.indezy.dto.FreelanceDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.mapper.FreelanceMapper;
import dev.byteworks.indezy.model.Freelance;
import dev.byteworks.indezy.model.enums.EmploymentStatus;
import dev.byteworks.indezy.repository.FreelanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FreelanceServiceTest {

    @Mock
    private FreelanceRepository freelanceRepository;

    @Mock
    private FreelanceMapper freelanceMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private FreelanceService freelanceService;

    private Freelance testFreelance;
    private FreelanceDto testFreelanceDto;

    @BeforeEach
    void setUp() {
        // Setup test freelance entity
        testFreelance = new Freelance();
        testFreelance.setId(1L);
        testFreelance.setFirstName("John");
        testFreelance.setLastName("Doe");
        testFreelance.setEmail("john.doe@example.com");
        testFreelance.setPhone("+33 6 12 34 56 78");
        testFreelance.setBirthDate(LocalDate.of(1990, 5, 15));
        testFreelance.setAddress("123 Rue de la Paix");
        testFreelance.setCity("Paris");
        testFreelance.setStatus(EmploymentStatus.FREELANCE);
        testFreelance.setNoticePeriodInDays(30);
        testFreelance.setAvailabilityDate(LocalDate.now().plusDays(30));
        testFreelance.setReversionRate(0.15);

        // Setup test freelance DTO
        testFreelanceDto = new FreelanceDto();
        testFreelanceDto.setId(1L);
        testFreelanceDto.setFirstName("John");
        testFreelanceDto.setLastName("Doe");
        testFreelanceDto.setEmail("john.doe@example.com");
        testFreelanceDto.setPhone("+33 6 12 34 56 78");
        testFreelanceDto.setBirthDate(LocalDate.of(1990, 5, 15));
        testFreelanceDto.setAddress("123 Rue de la Paix");
        testFreelanceDto.setCity("Paris");
        testFreelanceDto.setStatus(EmploymentStatus.FREELANCE);
        testFreelanceDto.setNoticePeriodInDays(30);
        testFreelanceDto.setAvailabilityDate(LocalDate.now().plusDays(30));
        testFreelanceDto.setReversionRate(0.15);
        testFreelanceDto.setFullName("John Doe");
        testFreelanceDto.setTotalProjects(3);
        testFreelanceDto.setAverageDailyRate(575.0);
    }

    @Test
    void findAll_ShouldReturnAllFreelances() {
        // Given
        List<Freelance> freelances = Arrays.asList(testFreelance);
        when(freelanceRepository.findAll()).thenReturn(freelances);
        when(freelanceMapper.toDto(testFreelance)).thenReturn(testFreelanceDto);

        // When
        List<FreelanceDto> result = freelanceService.findAll();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("John");
        assertThat(result.get(0).getLastName()).isEqualTo("Doe");
        assertThat(result.get(0).getEmail()).isEqualTo("john.doe@example.com");
        verify(freelanceRepository).findAll();
        verify(freelanceMapper).toDto(testFreelance);
    }

    @Test
    void findById_WhenFreelanceExists_ShouldReturnFreelance() {
        // Given
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(freelanceMapper.toDto(testFreelance)).thenReturn(testFreelanceDto);

        // When
        FreelanceDto result = freelanceService.findById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
        verify(freelanceRepository).findById(1L);
        verify(freelanceMapper).toDto(testFreelance);
    }

    @Test
    void findById_WhenFreelanceNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(freelanceRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> freelanceService.findById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Freelance not found with id: 1");
        
        verify(freelanceRepository).findById(1L);
        verify(freelanceMapper, never()).toDto(any());
    }

    @Test
    void findByIdWithProjects_WhenFreelanceExists_ShouldReturnFreelanceWithProjects() {
        // Given
        when(freelanceRepository.findByIdWithProjects(1L)).thenReturn(Optional.of(testFreelance));
        when(freelanceMapper.toDto(testFreelance)).thenReturn(testFreelanceDto);

        // When
        FreelanceDto result = freelanceService.findByIdWithProjects(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTotalProjects()).isEqualTo(3);
        verify(freelanceRepository).findByIdWithProjects(1L);
        verify(freelanceMapper).toDto(testFreelance);
    }

    @Test
    void findByIdWithProjects_WhenFreelanceNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(freelanceRepository.findByIdWithProjects(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> freelanceService.findByIdWithProjects(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Freelance not found with id: 1");
        
        verify(freelanceRepository).findByIdWithProjects(1L);
        verify(freelanceMapper, never()).toDto(any());
    }

    @Test
    void findByEmail_WhenFreelanceExists_ShouldReturnFreelance() {
        // Given
        when(freelanceRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(testFreelance));
        when(freelanceMapper.toDto(testFreelance)).thenReturn(testFreelanceDto);

        // When
        FreelanceDto result = freelanceService.findByEmail("john.doe@example.com");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
        verify(freelanceRepository).findByEmail("john.doe@example.com");
        verify(freelanceMapper).toDto(testFreelance);
    }

    @Test
    void findByEmail_WhenFreelanceNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(freelanceRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> freelanceService.findByEmail("nonexistent@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Freelance not found with email: nonexistent@example.com");
        
        verify(freelanceRepository).findByEmail("nonexistent@example.com");
        verify(freelanceMapper, never()).toDto(any());
    }

    @Test
    void create_WithValidData_ShouldCreateFreelance() {
        // Given
        when(freelanceRepository.existsByEmail("john.doe@example.com")).thenReturn(false);
        when(freelanceMapper.toEntity(testFreelanceDto)).thenReturn(testFreelance);
        when(freelanceRepository.save(testFreelance)).thenReturn(testFreelance);
        when(freelanceMapper.toDto(testFreelance)).thenReturn(testFreelanceDto);

        // When
        FreelanceDto result = freelanceService.create(testFreelanceDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(result.getFirstName()).isEqualTo("John");
        verify(freelanceRepository).existsByEmail("john.doe@example.com");
        verify(freelanceMapper).toEntity(testFreelanceDto);
        verify(freelanceRepository).save(testFreelance);
        verify(freelanceMapper).toDto(testFreelance);
    }

    @Test
    void create_WithExistingEmail_ShouldThrowIllegalArgumentException() {
        // Given
        when(freelanceRepository.existsByEmail("john.doe@example.com")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> freelanceService.create(testFreelanceDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Freelance already exists with email: john.doe@example.com");
        
        verify(freelanceRepository).existsByEmail("john.doe@example.com");
        verify(freelanceRepository, never()).save(any());
    }

    @Test
    void update_WithValidData_ShouldUpdateFreelance() {
        // Given
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(freelanceRepository.save(testFreelance)).thenReturn(testFreelance);
        when(freelanceMapper.toDto(testFreelance)).thenReturn(testFreelanceDto);

        // When
        FreelanceDto result = freelanceService.update(1L, testFreelanceDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(freelanceRepository).findById(1L);
        verify(freelanceMapper).updateEntity(testFreelanceDto, testFreelance);
        verify(freelanceRepository).save(testFreelance);
        verify(freelanceMapper).toDto(testFreelance);
    }

    @Test
    void update_WhenFreelanceNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(freelanceRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> freelanceService.update(1L, testFreelanceDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Freelance not found with id: 1");
        
        verify(freelanceRepository).findById(1L);
        verify(freelanceRepository, never()).save(any());
    }

    @Test
    void delete_WhenFreelanceExists_ShouldDeleteFreelance() {
        // Given
        when(freelanceRepository.existsById(1L)).thenReturn(true);

        // When
        freelanceService.delete(1L);

        // Then
        verify(freelanceRepository).existsById(1L);
        verify(freelanceRepository).deleteById(1L);
    }

    @Test
    void delete_WhenFreelanceNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(freelanceRepository.existsById(1L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> freelanceService.delete(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Freelance not found with id: 1");
        
        verify(freelanceRepository).existsById(1L);
        verify(freelanceRepository, never()).deleteById(any());
    }

    @Test
    void existsByEmail_WhenEmailExists_ShouldReturnTrue() {
        // Given
        when(freelanceRepository.existsByEmail("john.doe@example.com")).thenReturn(true);

        // When
        boolean result = freelanceService.existsByEmail("john.doe@example.com");

        // Then
        assertThat(result).isTrue();
        verify(freelanceRepository).existsByEmail("john.doe@example.com");
    }

    @Test
    void existsByEmail_WhenEmailNotExists_ShouldReturnFalse() {
        // Given
        when(freelanceRepository.existsByEmail("nonexistent@example.com")).thenReturn(false);

        // When
        boolean result = freelanceService.existsByEmail("nonexistent@example.com");

        // Then
        assertThat(result).isFalse();
        verify(freelanceRepository).existsByEmail("nonexistent@example.com");
    }
}
