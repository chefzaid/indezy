package dev.byteworks.indezy.service;

import dev.byteworks.indezy.constants.ErrorMessages;
import dev.byteworks.indezy.dto.FreelanceDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.mapper.FreelanceMapper;
import dev.byteworks.indezy.model.Freelance;
import dev.byteworks.indezy.repository.FreelanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

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

    @Transactional(readOnly = true)
    public FreelanceDto findByEmail(String email) {
        log.debug("Finding freelance by email: {}", email);
        Freelance freelance = freelanceRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.FREELANCE_EMAIL_NOT_FOUND, email)));
        return freelanceMapper.toDto(freelance);
    }

    @Transactional(readOnly = true)
    public FreelanceDto findByIdWithProjects(Long id) {
        log.debug("Finding freelance with projects by id: {}", id);
        Freelance freelance = freelanceRepository.findByIdWithProjects(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.FREELANCE_NOT_FOUND, id)));
        return freelanceMapper.toDto(freelance);
    }

    public FreelanceDto create(FreelanceDto freelanceDto) {
        log.debug("Creating new freelance: {}", freelanceDto.getEmail());
        
        if (freelanceRepository.existsByEmail(freelanceDto.getEmail())) {
            throw new IllegalArgumentException(String.format(ErrorMessages.FREELANCE_EMAIL_EXISTS, freelanceDto.getEmail()));
        }

        Freelance freelance = freelanceMapper.toEntity(freelanceDto);
        Freelance savedFreelance = freelanceRepository.save(freelance);
        
        log.info("Created freelance with id: {}", savedFreelance.getId());
        return freelanceMapper.toDto(savedFreelance);
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

    public void delete(Long id) {
        log.debug("Deleting freelance with id: {}", id);
        
        if (!freelanceRepository.existsById(id)) {
            throw new ResourceNotFoundException(String.format(ErrorMessages.FREELANCE_NOT_FOUND, id));
        }

        freelanceRepository.deleteById(id);
        log.info("Deleted freelance with id: {}", id);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return freelanceRepository.existsByEmail(email);
    }

    public void updatePassword(Long id, String newPassword) {
        log.debug("Updating password for freelance with id: {}", id);
        
        Freelance freelance = freelanceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Freelance not found with id: " + id));

        freelance.setPasswordHash(passwordEncoder.encode(newPassword));
        freelanceRepository.save(freelance);
        
        log.info("Updated password for freelance with id: {}", id);
    }
}
