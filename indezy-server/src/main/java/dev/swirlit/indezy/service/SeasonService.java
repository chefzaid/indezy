package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.SeasonDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.SeasonMapper;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Season;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Job-hunting seasons: bounded prospection periods, each with its own pipeline and dashboard.
 * Creating a season adopts the workspace's unassigned opportunities created within its dates, and
 * new opportunities join the season running on the day they are created.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SeasonService {

    private final SeasonRepository seasonRepository;
    private final FreelanceRepository freelanceRepository;
    private final ProjectRepository projectRepository;
    private final SeasonMapper seasonMapper;

    @Transactional(readOnly = true)
    public List<SeasonDto> findByFreelanceId(Long freelanceId) {
        LocalDate today = today();
        return seasonRepository.findByFreelanceIdOrderByStartDateDesc(freelanceId).stream()
            .map(season -> toDto(season, today))
            .toList();
    }

    @Transactional(readOnly = true)
    public SeasonDto findById(Long id) {
        return toDto(getSeason(id), today());
    }

    public SeasonDto create(SeasonDto dto) {
        validateDates(dto);
        Freelance freelance = freelanceRepository.findById(dto.getFreelanceId())
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format(ErrorMessages.FREELANCE_NOT_FOUND, dto.getFreelanceId())));

        Season season = seasonMapper.toEntity(dto);
        season.setFreelance(freelance);
        Season saved = seasonRepository.save(season);
        int adopted = adoptUnassignedProjects(saved);
        log.info("Created season {} for freelance {} and attached {} existing opportunities",
            saved.getId(), freelance.getId(), adopted);
        return toDto(saved, today());
    }

    public SeasonDto update(Long id, SeasonDto dto) {
        validateDates(dto);
        Season season = getSeason(id);
        seasonMapper.updateEntity(dto, season);
        Season saved = seasonRepository.save(season);
        adoptUnassignedProjects(saved);
        return toDto(saved, today());
    }

    /** Deletes a season; its opportunities are kept and simply no longer belong to a season. */
    public void delete(Long id) {
        Season season = getSeason(id);
        projectRepository.clearSeason(season.getId());
        seasonRepository.delete(season);
        log.info("Deleted season {}", id);
    }

    /**
     * The season a new opportunity joins: the running season that started most recently, if any.
     */
    @Transactional(readOnly = true)
    public Optional<Season> findCurrentSeason(Long freelanceId) {
        LocalDate today = today();
        return seasonRepository.findByFreelanceIdOrderByStartDateDesc(freelanceId).stream()
            .filter(season -> season.isActiveOn(today))
            .findFirst();
    }

    /** Loads a season and checks it belongs to the given workspace. */
    @Transactional(readOnly = true)
    public Season getSeasonOfFreelance(Long seasonId, Long freelanceId) {
        Season season = getSeason(seasonId);
        if (freelanceId != null && !season.getFreelance().getId().equals(freelanceId)) {
            throw new ResourceNotFoundException(String.format(ErrorMessages.SEASON_NOT_FOUND, seasonId));
        }
        return season;
    }

    private int adoptUnassignedProjects(Season season) {
        LocalDateTime from = season.getStartDate().atStartOfDay();
        LocalDateTime to = (season.getEndDate() != null ? season.getEndDate() : today()).plusDays(1).atStartOfDay();
        List<Project> projects = projectRepository.findUnassignedCreatedBetween(
            season.getFreelance().getId(), from, to);
        projects.forEach(project -> project.setSeason(season));
        projectRepository.saveAll(projects);
        return projects.size();
    }

    private Season getSeason(Long id) {
        return seasonRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.SEASON_NOT_FOUND, id)));
    }

    private SeasonDto toDto(Season season, LocalDate today) {
        SeasonDto dto = seasonMapper.toDto(season);
        dto.setActive(season.isActiveOn(today));
        dto.setProjectCount((int) projectRepository.countBySeasonId(season.getId()));
        return dto;
    }

    private static void validateDates(SeasonDto dto) {
        if (dto.getStartDate() != null && dto.getEndDate() != null && dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new IllegalArgumentException(ErrorMessages.INVALID_DATE_RANGE);
        }
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }
}
