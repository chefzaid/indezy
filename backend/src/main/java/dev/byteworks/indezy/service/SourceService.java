package dev.byteworks.indezy.service;

import dev.byteworks.indezy.dto.SourceDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.mapper.SourceMapper;
import dev.byteworks.indezy.model.Freelance;
import dev.byteworks.indezy.model.Source;
import dev.byteworks.indezy.model.enums.SourceType;
import dev.byteworks.indezy.repository.FreelanceRepository;
import dev.byteworks.indezy.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SourceService {

    private final SourceRepository sourceRepository;
    private final FreelanceRepository freelanceRepository;
    private final SourceMapper sourceMapper;

    @Transactional(readOnly = true)
    public List<SourceDto> findAll() {
        log.debug("Finding all sources");
        return sourceRepository.findAll()
            .stream()
            .map(sourceMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public SourceDto findById(Long id) {
        log.debug("Finding source by id: {}", id);
        Source source = sourceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Source not found with id: " + id));
        return sourceMapper.toDto(source);
    }

    public SourceDto create(SourceDto sourceDto) {
        log.debug("Creating new source: {}", sourceDto.getName());
        
        // Validate freelance exists
        Freelance freelance = freelanceRepository.findById(sourceDto.getFreelanceId())
            .orElseThrow(() -> new ResourceNotFoundException("Freelance not found with id: " + sourceDto.getFreelanceId()));
        
        Source source = sourceMapper.toEntity(sourceDto);
        source.setId(null); // Ensure ID is null for creation
        source.setFreelance(freelance);
        
        Source savedSource = sourceRepository.save(source);
        log.debug("Created source with id: {}", savedSource.getId());
        
        return sourceMapper.toDto(savedSource);
    }

    public SourceDto update(Long id, SourceDto sourceDto) {
        log.debug("Updating source with id: {}", id);
        
        Source existingSource = sourceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Source not found with id: " + id));
        
        // Validate freelance exists if changed
        if (!existingSource.getFreelance().getId().equals(sourceDto.getFreelanceId())) {
            Freelance freelance = freelanceRepository.findById(sourceDto.getFreelanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Freelance not found with id: " + sourceDto.getFreelanceId()));
            existingSource.setFreelance(freelance);
        }
        
        sourceMapper.updateEntity(sourceDto, existingSource);
        
        Source updatedSource = sourceRepository.save(existingSource);
        log.debug("Updated source with id: {}", updatedSource.getId());
        
        return sourceMapper.toDto(updatedSource);
    }

    public void delete(Long id) {
        log.debug("Deleting source with id: {}", id);
        
        Source source = sourceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Source not found with id: " + id));
        
        sourceRepository.delete(source);
        log.debug("Deleted source with id: {}", id);
    }

    @Transactional(readOnly = true)
    public List<SourceDto> findByFreelanceId(Long freelanceId) {
        log.debug("Finding sources by freelance id: {}", freelanceId);
        return sourceRepository.findByFreelanceId(freelanceId)
            .stream()
            .map(sourceMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SourceDto> findByFreelanceIdAndType(Long freelanceId, SourceType type) {
        log.debug("Finding sources by freelance id: {} and type: {}", freelanceId, type);
        return sourceRepository.findByFreelanceIdAndType(freelanceId, type)
            .stream()
            .map(sourceMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SourceDto> findByFreelanceIdAndIsListing(Long freelanceId, Boolean isListing) {
        log.debug("Finding sources by freelance id: {} and isListing: {}", freelanceId, isListing);
        return sourceRepository.findByFreelanceIdAndIsListing(freelanceId, isListing)
            .stream()
            .map(sourceMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SourceDto> findByFreelanceIdAndPopularityRatingGreaterThanEqual(Long freelanceId, Integer minRating) {
        log.debug("Finding sources by freelance id: {} and popularity rating >= {}", freelanceId, minRating);
        return sourceRepository.findByFreelanceIdAndPopularityRatingGreaterThanEqual(freelanceId, minRating)
            .stream()
            .map(sourceMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SourceDto> findByFreelanceIdAndUsefulnessRatingGreaterThanEqual(Long freelanceId, Integer minRating) {
        log.debug("Finding sources by freelance id: {} and usefulness rating >= {}", freelanceId, minRating);
        return sourceRepository.findByFreelanceIdAndUsefulnessRatingGreaterThanEqual(freelanceId, minRating)
            .stream()
            .map(sourceMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public SourceDto findByIdWithProjects(Long id) {
        log.debug("Finding source by id with projects: {}", id);
        Source source = sourceRepository.findByIdWithProjects(id)
            .orElseThrow(() -> new ResourceNotFoundException("Source not found with id: " + id));
        return sourceMapper.toDto(source);
    }

    @Transactional(readOnly = true)
    public Double getAveragePopularityRating(Long freelanceId) {
        log.debug("Getting average popularity rating for freelance id: {}", freelanceId);
        return sourceRepository.findAveragePopularityRatingByFreelanceId(freelanceId);
    }

    @Transactional(readOnly = true)
    public Double getAverageUsefulnessRating(Long freelanceId) {
        log.debug("Getting average usefulness rating for freelance id: {}", freelanceId);
        return sourceRepository.findAverageUsefulnessRatingByFreelanceId(freelanceId);
    }

    @Transactional(readOnly = true)
    public List<SourceType> findDistinctTypesByFreelanceId(Long freelanceId) {
        log.debug("Finding distinct source types by freelance id: {}", freelanceId);
        return sourceRepository.findDistinctTypesByFreelanceId(freelanceId);
    }
}
