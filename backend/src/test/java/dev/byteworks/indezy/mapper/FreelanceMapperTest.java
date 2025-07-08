package dev.byteworks.indezy.mapper;

import dev.byteworks.indezy.dto.FreelanceDto;
import dev.byteworks.indezy.model.Freelance;
import dev.byteworks.indezy.model.enums.EmploymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FreelanceMapper
 * Tests mapping between Freelance entities and FreelanceDto
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class FreelanceMapperTest {

    @Autowired
    private FreelanceMapper freelanceMapper;

    private Freelance testFreelance;
    private FreelanceDto testFreelanceDto;

    @BeforeEach
    void setUp() {

        // Create test freelance
        testFreelance = new Freelance();
        testFreelance.setId(1L);
        testFreelance.setFirstName("John");
        testFreelance.setLastName("Doe");
        testFreelance.setEmail("john.doe@example.com");
        testFreelance.setPhone("123-456-7890");
        testFreelance.setStatus(EmploymentStatus.FREELANCE);

        // Create test FreelanceDto
        testFreelanceDto = new FreelanceDto();
        testFreelanceDto.setId(1L);
        testFreelanceDto.setFirstName("John");
        testFreelanceDto.setLastName("Doe");
        testFreelanceDto.setEmail("john.doe@example.com");
        testFreelanceDto.setPhone("123-456-7890");
        testFreelanceDto.setStatus(EmploymentStatus.FREELANCE);
    }

    @Test
    void toDto_WithValidFreelance_ShouldMapToFreelanceDto() {
        FreelanceDto result = freelanceMapper.toDto(testFreelance);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testFreelance.getId());
        assertThat(result.getFirstName()).isEqualTo(testFreelance.getFirstName());
        assertThat(result.getLastName()).isEqualTo(testFreelance.getLastName());
        assertThat(result.getEmail()).isEqualTo(testFreelance.getEmail());
        assertThat(result.getPhone()).isEqualTo(testFreelance.getPhone());
        assertThat(result.getStatus()).isEqualTo(testFreelance.getStatus());
    }

    @Test
    void toDto_WithNullFreelance_ShouldReturnNull() {
        FreelanceDto result = freelanceMapper.toDto(null);

        assertThat(result).isNull();
    }

    @Test
    void toEntity_WithValidFreelanceDto_ShouldMapToFreelance() {
        Freelance result = freelanceMapper.toEntity(testFreelanceDto);

        assertThat(result).isNotNull();
        // ID should be null for new entities (ignored in mapping)
        assertThat(result.getId()).isNull();
        assertThat(result.getFirstName()).isEqualTo(testFreelanceDto.getFirstName());
        assertThat(result.getLastName()).isEqualTo(testFreelanceDto.getLastName());
        assertThat(result.getEmail()).isEqualTo(testFreelanceDto.getEmail());
        assertThat(result.getPhone()).isEqualTo(testFreelanceDto.getPhone());
        assertThat(result.getStatus()).isEqualTo(testFreelanceDto.getStatus());
    }

    @Test
    void toEntity_WithNullFreelanceDto_ShouldReturnNull() {
        Freelance result = freelanceMapper.toEntity(null);

        assertThat(result).isNull();
    }

    @Test
    void updateEntity_WithValidData_ShouldUpdateFreelance() {
        Freelance existingFreelance = new Freelance();
        existingFreelance.setId(1L);
        existingFreelance.setFirstName("Old Name");
        existingFreelance.setLastName("Old Last");

        freelanceMapper.updateEntity(testFreelanceDto, existingFreelance);

        assertThat(existingFreelance.getFirstName()).isEqualTo(testFreelanceDto.getFirstName());
        assertThat(existingFreelance.getLastName()).isEqualTo(testFreelanceDto.getLastName());
        assertThat(existingFreelance.getEmail()).isEqualTo(testFreelanceDto.getEmail());
        assertThat(existingFreelance.getPhone()).isEqualTo(testFreelanceDto.getPhone());
        assertThat(existingFreelance.getStatus()).isEqualTo(testFreelanceDto.getStatus());
    }

    @Test
    void updateEntity_WithNullDto_ShouldNotUpdateFreelance() {
        Freelance existingFreelance = new Freelance();
        existingFreelance.setId(1L);
        existingFreelance.setFirstName("Original Name");

        freelanceMapper.updateEntity(null, existingFreelance);

        assertThat(existingFreelance.getFirstName()).isEqualTo("Original Name");
    }

    @Test
    void updateEntity_WithNullEntity_ShouldThrowException() {
        // MapStruct generated code will throw NullPointerException when target is null
        // This is expected behavior - we should not try to update a null entity
        assertThatThrownBy(() -> freelanceMapper.updateEntity(testFreelanceDto, null))
                .isInstanceOf(NullPointerException.class);
    }
}
