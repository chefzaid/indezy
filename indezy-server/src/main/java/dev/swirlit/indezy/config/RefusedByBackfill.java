package dev.swirlit.indezy.config;

import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.RefusedBy;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Says who refused the opportunities refused before Indezy asked, when their reason tells: the freelance turns down a
 * rate too low or leaves for another offer; the recruiter fills the position, stops answering, finds the profile
 * unsuited, or sees the client cancel. One refused for another reason, or for none, keeps no side. It only fills a
 * side left unsaid, so running it again changes nothing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RequiredArgsConstructor
@Slf4j
public class RefusedByBackfill implements ApplicationRunner {

    static final Map<RefusedBy, List<LostReason>> REASONS_BY_SIDE = Map.of(
        RefusedBy.FREELANCE, List.of(LostReason.RATE_TOO_LOW, LostReason.ACCEPTED_OTHER_OFFER),
        RefusedBy.RECRUITER, List.of(LostReason.POSITION_FILLED, LostReason.NO_RESPONSE, LostReason.PROFILE_MISMATCH,
            LostReason.CLIENT_CANCELED));

    private final ProjectRepository projectRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        REASONS_BY_SIDE.forEach((side, reasons) -> {
            int told = projectRepository.setRefusedByWhereUnsaid(side, reasons);
            if (told > 0) {
                log.info("{} refused opportunities now say they were refused by the {}", told, side);
            }
        });
    }
}
