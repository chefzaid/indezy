package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Source;
import dev.swirlit.indezy.model.enums.OfferLeftOutSite;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.SourceType;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Gives a workspace every job board Indezy knows as a source: the boards of the offer feed and the sites it does not read
 * (see {@link OfferLeftOutSite}), each with its home link. A workspace gets them once, when it is created (or, for one
 * created before, on the first start that knows them): a source deleted since stays deleted, and a source already named
 * like a board, case aside, is kept as it is. The offer import finds them by name.
 */
@Service
@RequiredArgsConstructor
public class JobBoardSources {

    /** A board as a source. */
    record Board(String name, String link, SourceType type, boolean listing) {
    }

    private final FreelanceRepository freelances;
    private final SourceRepository sources;

    /** Every board, the feed's first. */
    static List<Board> catalog() {
        return Stream.concat(
            Stream.of(OfferSite.values()).map(site -> new Board(site.getDisplayName(), site.getHomeUrl(), SourceType.JOB_BOARD, true)),
            Stream.of(OfferLeftOutSite.values()).map(site -> new Board(site.getDisplayName(), site.getHomeUrl(),
                site == OfferLeftOutSite.LINKEDIN ? SourceType.SOCIAL_MEDIA : SourceType.JOB_BOARD,
                site.getKind() != OfferLeftOutSite.Kind.MATCHING)))
            .toList();
    }

    /** Adds the boards the workspace has no source for, unless it was given them already; how many it added. */
    @Transactional
    public int seed(Long freelanceId) {
        if (freelances.claimJobBoardSources(freelanceId) == 0) {
            return 0;
        }
        Set<String> named = sources.findByFreelanceId(freelanceId).stream()
            .map(source -> source.getName().toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(HashSet::new));
        Freelance freelance = freelances.getReferenceById(freelanceId);
        List<Source> added = catalog().stream()
            .filter(board -> named.add(board.name().toLowerCase(Locale.ROOT)))
            .map(board -> source(board, freelance))
            .toList();
        sources.saveAll(added);
        return added.size();
    }

    private static Source source(Board board, Freelance freelance) {
        Source source = new Source();
        source.setName(board.name());
        source.setType(board.type());
        source.setLink(board.link());
        source.setIsListing(board.listing());
        source.setFreelance(freelance);
        return source;
    }
}
