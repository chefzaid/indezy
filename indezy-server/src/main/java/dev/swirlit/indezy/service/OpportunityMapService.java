package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OpportunityMapDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.scraping.GeoLocator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Places the workspace's opportunities on a map (see docs/features.md#opportunity-map): the home address and each
 * opportunity still on the board at its place of work or its client's address, located by the national address search
 * as the commute estimate locates them (each address once, then kept).
 */
@Service
@RequiredArgsConstructor
public class OpportunityMapService {

    private final FreelanceRepository freelances;
    private final ProjectRepository projects;
    private final GeoLocator geoLocator;

    @Transactional(readOnly = true)
    public OpportunityMapDto map(Long freelanceId) {
        Freelance freelance = freelances.findById(freelanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Freelance not found: " + freelanceId));
        String origin = CommuteService.address(freelance.getAddress(), freelance.getCity());
        OpportunityMapDto.Point home = origin.isBlank() ? null :
            locate(origin, new HashMap<>()).map(place -> new OpportunityMapDto.Point(place.latitude(), place.longitude())).orElse(null);
        Map<String, Optional<GeoLocator.Place>> located = new HashMap<>();
        List<OpportunityMapDto.Pin> pins = new ArrayList<>();
        int unplaced = 0;
        for (Project project : projects.findByFreelanceId(freelanceId)) {
            if (project.getArchivedAt() != null) {
                continue;
            }
            String destination = CommuteService.destination(project);
            Optional<GeoLocator.Place> place = destination.isBlank() ? Optional.empty() : locate(destination, located);
            if (place.isEmpty()) {
                unplaced++;
                continue;
            }
            pins.add(new OpportunityMapDto.Pin(project.getId(), project.getRole(),
                project.getClient() == null ? null : project.getClient().getCompanyName(), project.getStatus(), destination,
                place.get().latitude(), place.get().longitude()));
        }
        return new OpportunityMapDto(home, pins, unplaced);
    }

    /** An address's place, asked once per map; none when the address search is not set up. */
    private Optional<GeoLocator.Place> locate(String address, Map<String, Optional<GeoLocator.Place>> located) {
        if (!geoLocator.canLocate()) {
            return Optional.empty();
        }
        return located.computeIfAbsent(address, geoLocator::locateAddress);
    }
}
