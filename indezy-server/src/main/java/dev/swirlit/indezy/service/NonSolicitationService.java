package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NonSolicitationDto;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The opportunities a non-solicitation clause still covers (see docs/features.md#non-solicitation-clauses): a won mission
 * signed through an ESN, with the clause's months recorded, forbids working for its end client without that ESN until
 * the months have passed after the mission's last day (no date yet while it runs open-ended).
 */
@Service
@RequiredArgsConstructor
public class NonSolicitationService {

    private final ProjectRepository projects;

    @Transactional(readOnly = true)
    public List<NonSolicitationDto> forFreelance(Long freelanceId, LocalDate today) {
        List<Project> all = projects.findByFreelanceId(freelanceId);
        List<NonSolicitationDto> covered = new ArrayList<>();
        for (Project mission : all) {
            if (mission.getStatus() != ProjectStatus.WON || mission.getMiddleman() == null || mission.getClient() == null ||
                mission.getNonSolicitationMonths() == null) {
                continue;
            }
            LocalDate last = mission.lastDay();
            LocalDate endsOn = last == null ? null : last.plusMonths(mission.getNonSolicitationMonths());
            if (endsOn != null && !endsOn.isAfter(today)) {
                continue;
            }
            for (Project opportunity : all) {
                if (opportunity != mission && opportunity.getArchivedAt() == null && sameClient(opportunity.getClient(), mission.getClient()) &&
                    !sameClient(opportunity.getMiddleman(), mission.getMiddleman())) {
                    covered.add(new NonSolicitationDto(opportunity.getId(), mission.getId(), mission.getRole(),
                        mission.getMiddleman().getCompanyName(), mission.getClient().getCompanyName(),
                        mission.getNonSolicitationMonths(), endsOn));
                }
            }
        }
        return covered;
    }

    private static boolean sameClient(Client one, Client other) {
        return one != null && other != null && Objects.equals(one.getId(), other.getId());
    }
}
