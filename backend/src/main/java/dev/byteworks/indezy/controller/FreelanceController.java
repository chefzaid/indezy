package dev.byteworks.indezy.controller;

import dev.byteworks.indezy.dto.FreelanceDto;
import dev.byteworks.indezy.service.FreelanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/freelances")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
public class FreelanceController {

    private final FreelanceService freelanceService;

    @GetMapping
    public ResponseEntity<List<FreelanceDto>> getAllFreelances() {
        log.debug("GET /freelances - Getting all freelances");
        List<FreelanceDto> freelances = freelanceService.findAll();
        return ResponseEntity.ok(freelances);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FreelanceDto> getFreelanceById(@PathVariable Long id) {
        log.debug("GET /freelances/{} - Getting freelance by id", id);
        FreelanceDto freelance = freelanceService.findById(id);
        return ResponseEntity.ok(freelance);
    }

    @GetMapping("/{id}/with-projects")
    public ResponseEntity<FreelanceDto> getFreelanceByIdWithProjects(@PathVariable Long id) {
        log.debug("GET /freelances/{}/with-projects - Getting freelance with projects", id);
        FreelanceDto freelance = freelanceService.findByIdWithProjects(id);
        return ResponseEntity.ok(freelance);
    }

    @GetMapping("/by-email")
    public ResponseEntity<FreelanceDto> getFreelanceByEmail(@RequestParam String email) {
        log.debug("GET /freelances/by-email?email={} - Getting freelance by email", email);
        FreelanceDto freelance = freelanceService.findByEmail(email);
        return ResponseEntity.ok(freelance);
    }

    @PostMapping
    public ResponseEntity<FreelanceDto> createFreelance(@Valid @RequestBody FreelanceDto freelanceDto) {
        log.debug("POST /freelances - Creating new freelance");
        FreelanceDto createdFreelance = freelanceService.create(freelanceDto);
        return new ResponseEntity<>(createdFreelance, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FreelanceDto> updateFreelance(@PathVariable Long id, 
                                                       @Valid @RequestBody FreelanceDto freelanceDto) {
        log.debug("PUT /freelances/{} - Updating freelance", id);
        FreelanceDto updatedFreelance = freelanceService.update(id, freelanceDto);
        return ResponseEntity.ok(updatedFreelance);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFreelance(@PathVariable Long id) {
        log.debug("DELETE /freelances/{} - Deleting freelance", id);
        freelanceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exists")
    public ResponseEntity<Boolean> checkEmailExists(@RequestParam String email) {
        log.debug("GET /freelances/exists?email={} - Checking if email exists", email);
        boolean exists = freelanceService.existsByEmail(email);
        return ResponseEntity.ok(exists);
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> updatePassword(@PathVariable Long id, 
                                             @RequestBody String newPassword) {
        log.debug("PATCH /freelances/{}/password - Updating password", id);
        freelanceService.updatePassword(id, newPassword);
        return ResponseEntity.ok().build();
    }
}
