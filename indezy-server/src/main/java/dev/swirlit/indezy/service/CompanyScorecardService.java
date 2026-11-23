package dev.swirlit.indezy.service;

import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * How a company (an ESN or a client) dealt with the workspace, from its own records only (see
 * docs/features.md#company-scorecard): how many opportunities, how many reached an offer, how long a process took to its
 * outcome, the ESN's margin on the client's rate, and how soon its contacts answer. This scorecard is the workspace's own;
 * the one across members ({@link EsnScorecardService}) is built from these, for the members who did not turn it off.
 */
@Service
@RequiredArgsConstructor
public class CompanyScorecardService {

    private final ClientRepository clients;
    private final ProjectRepository projects;
    private final ContactInteractionService interactions;

    /**
     * The scorecard: {@code offerRate} the share, in percent, of the opportunities past the interviews (an offer, won or
     * turned down) that reached an offer or were won, null without any; {@code processDays} the median days from an
     * opportunity's creation to its last interview step, over those settled (won or turned down) with a dated step, and
     * {@code processes} how many; {@code marginMin} and {@code marginMax} the ESN's lowest and highest share, in percent,
     * of the client's daily rate kept over the freelancer's, over the opportunities through it with both rates
     * ({@code margins} how many); {@code answerDays} its contacts' usual answer time, null until known.
     */
    public record Scorecard(int opportunities, int open, int won, Integer offerRate, Integer processDays, int processes,
                            Integer marginMin, Integer marginMax, int margins, Integer answerDays) {
    }

    @Transactional(readOnly = true)
    public Scorecard scorecard(Long companyId) {
        Client company = clients.findById(companyId).orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + companyId));
        List<Project> all = projects.findByCompany(companyId);
        int open = 0;
        int won = 0;
        int pastInterviews = 0;
        int offers = 0;
        List<Long> processes = new ArrayList<>();
        List<Integer> margins = new ArrayList<>();
        for (Project project : all) {
            ProjectStatus status = project.getStatus();
            if (status == ProjectStatus.WON) {
                won++;
            } else if (!status.isRefused()) {
                open++;
            }
            boolean throughOffer = status == ProjectStatus.OFFER || status == ProjectStatus.WON;
            if (throughOffer || status.isRefused()) {
                pastInterviews++;
                offers += throughOffer ? 1 : 0;
            }
            if ((status == ProjectStatus.WON || status.isRefused()) && project.getCreatedAt() != null) {
                project.getSteps().stream().map(InterviewStep::getDate).filter(Objects::nonNull).max(Comparable::compareTo)
                    .map(last -> ChronoUnit.DAYS.between(project.getCreatedAt().toLocalDate(), LocalDate.from(last)))
                    .filter(days -> days >= 0).ifPresent(processes::add);
            }
            if (project.getMiddleman() != null && companyId.equals(project.getMiddleman().getId()) && project.getClientDailyRate() != null &&
                project.getDailyRate() != null && project.getDailyRate() > 0 && project.getClientDailyRate() >= project.getDailyRate()) {
                margins.add((int) Math.round(100.0 * (project.getClientDailyRate() - project.getDailyRate()) / project.getClientDailyRate()));
            }
        }
        Integer offerRate = pastInterviews == 0 ? null : (int) Math.round(100.0 * offers / pastInterviews);
        Integer answerDays = interactions.usualAnswerDays(company.getFreelance().getId()).get(companyId);
        return new Scorecard(all.size(), open, won, offerRate, median(processes), processes.size(),
            margins.stream().min(Integer::compare).orElse(null), margins.stream().max(Integer::compare).orElse(null), margins.size(),
            answerDays);
    }

    private static Integer median(List<Long> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return (int) (sorted.size() % 2 == 1 ? sorted.get(middle) : Math.round((sorted.get(middle - 1) + sorted.get(middle)) / 2.0));
    }
}
