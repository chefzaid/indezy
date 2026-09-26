package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.FreelanceDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.FreelanceMapper;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.FreelanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FreelanceService {

    private final FreelanceRepository freelanceRepository;
    private final FreelanceMapper freelanceMapper;

    @Transactional(readOnly = true)
    public List<FreelanceDto> findAll() {
        log.debug("Finding all freelances");
        return freelanceRepository.findAll()
            .stream()
            .map(freelanceMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public FreelanceDto findById(Long id) {
        log.debug("Finding freelance by id: {}", id);
        Freelance freelance = freelanceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.FREELANCE_NOT_FOUND, id)));
        return freelanceMapper.toDto(freelance);
    }

    public FreelanceDto update(Long id, FreelanceDto freelanceDto) {
        log.debug("Updating freelance with id: {}", id);
        
        Freelance existingFreelance = freelanceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.FREELANCE_NOT_FOUND, id)));

        // Check if email is being changed and if it's already taken
        if (!existingFreelance.getEmail().equals(freelanceDto.getEmail()) && 
            freelanceRepository.existsByEmail(freelanceDto.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + freelanceDto.getEmail());
        }

        freelanceMapper.updateEntity(freelanceDto, existingFreelance);
        Freelance updatedFreelance = freelanceRepository.save(existingFreelance);
        
        log.info("Updated freelance with id: {}", updatedFreelance.getId());
        return freelanceMapper.toDto(updatedFreelance);
    }

}
