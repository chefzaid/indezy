package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.SeasonDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.SeasonMapper;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Season;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.SeasonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeasonServiceTest {

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private FreelanceRepository freelanceRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Spy
    private SeasonMapper seasonMapper = Mappers.getMapper(SeasonMapper.class);

    @InjectMocks
    private SeasonService seasonService;

    private Freelance freelance;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        freelance = new Freelance();
        freelance.setId(1L);
    }

    @Test
    void create_ShouldAdoptUnassignedOpportunitiesCreatedWithinTheSeason() {
        SeasonDto dto = seasonDto("Autumn search", today.minusDays(30), null);
        Project inWindow = new Project();
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(freelance));
        when(seasonRepository.save(any(Season.class))).thenAnswer(invocation -> {
            Season season = invocation.getArgument(0);
            season.setId(7L);
            return season;
        });
        when(projectRepository.findUnassignedCreatedBetween(eq(1L), any(), any())).thenReturn(List.of(inWindow));
        when(projectRepository.countBySeasonId(7L)).thenReturn(1L);

        SeasonDto created = seasonService.create(dto);

        assertThat(created.getId()).isEqualTo(7L);
        assertThat(created.getActive()).isTrue();
        assertThat(created.getProjectCount()).isEqualTo(1);
        assertThat(inWindow.getSeason()).isNotNull();
        assertThat(inWindow.getSeason().getId()).isEqualTo(7L);
        // An open season adopts everything created from its start until today (inclusive).
        verify(projectRepository).findUnassignedCreatedBetween(1L,
            today.minusDays(30).atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    @Test
    void create_WithEndBeforeStart_ShouldBeRejected() {
        SeasonDto dto = seasonDto("Backwards", today, today.minusDays(1));

        assertThatThrownBy(() -> seasonService.create(dto)).isInstanceOf(IllegalArgumentException.class);
        verify(seasonRepository, never()).save(any());
    }

    @Test
    void findCurrentSeason_ShouldPickTheRunningSeasonThatStartedLast() {
        Season closed = season(1L, today.minusDays(200), today.minusDays(100));
        Season older = season(2L, today.minusDays(60), null);
        Season latest = season(3L, today.minusDays(10), today.plusDays(20));
        Season upcoming = season(4L, today.plusDays(5), null);
        when(seasonRepository.findByFreelanceIdOrderByStartDateDesc(1L))
            .thenReturn(List.of(upcoming, latest, older, closed));

        assertThat(seasonService.findCurrentSeason(1L)).containsSame(latest);
    }

    @Test
    void findCurrentSeason_WithoutRunningSeason_ShouldBeEmpty() {
        when(seasonRepository.findByFreelanceIdOrderByStartDateDesc(1L))
            .thenReturn(List.of(season(1L, today.minusDays(200), today.minusDays(100))));

        assertThat(seasonService.findCurrentSeason(1L)).isEmpty();
    }

    @Test
    void delete_ShouldDetachOpportunitiesBeforeRemovingTheSeason() {
        Season season = season(5L, today.minusDays(10), null);
        when(seasonRepository.findById(5L)).thenReturn(Optional.of(season));

        seasonService.delete(5L);

        verify(projectRepository).clearSeason(5L);
        verify(seasonRepository).delete(season);
    }

    @Test
    void getSeasonOfFreelance_FromAnotherWorkspace_ShouldBeNotFound() {
        when(seasonRepository.findById(5L)).thenReturn(Optional.of(season(5L, today, null)));

        assertThatThrownBy(() -> seasonService.getSeasonOfFreelance(5L, 2L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void isActiveOn_ShouldIncludeBothBoundaries() {
        Season season = season(1L, today.minusDays(5), today);

        assertThat(season.isActiveOn(today.minusDays(5))).isTrue();
        assertThat(season.isActiveOn(today)).isTrue();
        assertThat(season.isActiveOn(today.minusDays(6))).isFalse();
        assertThat(season.isActiveOn(today.plusDays(1))).isFalse();
    }

    private SeasonDto seasonDto(String name, LocalDate start, LocalDate end) {
        SeasonDto dto = new SeasonDto();
        dto.setName(name);
        dto.setStartDate(start);
        dto.setEndDate(end);
        dto.setFreelanceId(1L);
        return dto;
    }

    private Season season(Long id, LocalDate start, LocalDate end) {
        Season season = new Season();
        season.setId(id);
        season.setName("Season " + id);
        season.setStartDate(start);
        season.setEndDate(end);
        season.setFreelance(freelance);
        season.setCreatedAt(LocalDateTime.now());
        return season;
    }
}
