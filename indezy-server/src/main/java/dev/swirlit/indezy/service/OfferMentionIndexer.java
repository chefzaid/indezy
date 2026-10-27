package dev.swirlit.indezy.service;

import dev.swirlit.indezy.repository.JobOfferSpecifications;
import dev.swirlit.indezy.repository.OfferMatch;
import dev.swirlit.indezy.util.ExpiringCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Records which profile terms each offer mentions (see docs/architecture.md#offer-search-at-scale), so profile matching
 * joins {@code offer_mentions} instead of reading every offer's text. A term (a folded spelling of a profile's skill)
 * joins the vocabulary when a profile first asks for it: the offers recorded so far are read for it once, in SQL, then
 * it is marked indexed. Offers not recorded yet (new, or whose text or skills changed) are recorded every minute
 * against the whole vocabulary, with the very function the offer cards use; until then they are matched on their text.
 * Both hold a lock on one row of {@code offer_terms}, so replicas never record the same offers or term at once.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfferMentionIndexer {

    /** Offers recorded per transaction. */
    static final int BATCH = 500;
    /** The vocabulary row every recording locks; a real term never holds '#'. */
    static final String LOCK = "#lock";

    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;
    private final ExpiringCache<String, Set<String>> indexed = new ExpiringCache<>(Duration.ofSeconds(30), 1);

    /** A transaction of its own: a profile is usually read within a read-only one. */
    private TransactionTemplate transactions() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /**
     * The terms among {@code terms} whose mentions are recorded, recording the missing ones first (in a transaction of
     * their own, as a profile is usually read within a read-only one).
     */
    public Set<String> indexed(Collection<String> terms) {
        Set<String> known = indexed.get("", clock.instant(), key -> readIndexed());
        Set<String> missing = terms.stream().filter(term -> !term.isBlank() && !known.contains(term)).collect(Collectors.toCollection(LinkedHashSet::new));
        if (missing.isEmpty()) {
            return terms.stream().filter(known::contains).collect(Collectors.toSet());
        }
        transactions().executeWithoutResult(status -> {
            lock();
            for (String term : missing) {
                addTerm(term);
            }
        });
        indexed.forget(key -> true);
        Set<String> now = indexed.get("", clock.instant(), key -> readIndexed());
        return terms.stream().filter(now::contains).collect(Collectors.toSet());
    }

    /** Records the offers not recorded yet, {@value #BATCH} at a time, up to {@code batches}; how many it recorded. */
    public int indexPending(int batches) {
        int recorded = 0;
        for (int i = 0; i < batches; i++) {
            Integer count = transactions().execute(status -> indexBatch());
            if (count == null || count == 0) {
                break;
            }
            recorded += count;
        }
        return recorded;
    }

    private int indexBatch() {
        lock();
        List<Map<String, Object>> offers = jdbc.queryForList(
            "SELECT id, search_text FROM job_offers WHERE mentions_indexed_at IS NULL ORDER BY id LIMIT " + BATCH);
        if (offers.isEmpty()) {
            return 0;
        }
        List<Long> ids = offers.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        Map<Long, List<String>> skills = new HashMap<>();
        jdbc.query("SELECT offer_id, skill FROM job_offer_skills WHERE offer_id IN (" + in + ")", row -> {
            skills.computeIfAbsent(row.getLong("offer_id"), id -> new ArrayList<>()).add(row.getString("skill"));
        });
        List<String> vocabulary = jdbc.queryForList("SELECT term FROM offer_terms WHERE term <> ?", String.class, LOCK);
        List<Object[]> rows = new ArrayList<>();
        for (Map<String, Object> offer : offers) {
            Long id = ((Number) offer.get("id")).longValue();
            String text = (String) offer.get("search_text");
            List<String> listed = skills.getOrDefault(id, List.of());
            for (String term : vocabulary) {
                if (OfferMatch.mentions(text, listed, term)) {
                    rows.add(new Object[] {id, term});
                }
            }
        }
        jdbc.update("DELETE FROM offer_mentions WHERE offer_id IN (" + in + ")");
        jdbc.batchUpdate("INSERT INTO offer_mentions (offer_id, term) VALUES (?, ?)", rows);
        // Recorded only while their text is the one read: an offer changed meanwhile is recorded again at the next run.
        Timestamp now = Timestamp.from(clock.instant());
        List<Object[]> read = new ArrayList<>();
        List<Object[]> readWithoutText = new ArrayList<>();
        for (Map<String, Object> offer : offers) {
            Object id = offer.get("id");
            Object text = offer.get("search_text");
            if (text == null) {
                readWithoutText.add(new Object[] {now, id});
            } else {
                read.add(new Object[] {now, id, text});
            }
        }
        jdbc.batchUpdate("UPDATE job_offers SET mentions_indexed_at = ? WHERE id = ? AND mentions_indexed_at IS NULL AND search_text = ?", read);
        jdbc.batchUpdate("UPDATE job_offers SET mentions_indexed_at = ? WHERE id = ? AND mentions_indexed_at IS NULL AND search_text IS NULL",
            readWithoutText);
        return ids.size();
    }

    /** Adds a term and records its mentions in the offers recorded so far, as OfferMatch.mentions reads them. */
    private void addTerm(String term) {
        List<Boolean> state = jdbc.queryForList("SELECT indexed FROM offer_terms WHERE term = ?", Boolean.class, term);
        if (state.contains(Boolean.TRUE)) {
            return;
        }
        if (state.isEmpty()) {
            jdbc.update("INSERT INTO offer_terms (term, indexed, created_at) VALUES (?, FALSE, ?)", term, Timestamp.from(clock.instant()));
        }
        int found;
        if (term.length() < OfferMatch.MIN_TEXT_SKILL_LENGTH) {
            found = jdbc.update("INSERT INTO offer_mentions (offer_id, term) SELECT DISTINCT s.offer_id, ? FROM job_offer_skills s " +
                "JOIN job_offers o ON o.id = s.offer_id WHERE o.mentions_indexed_at IS NOT NULL AND LOWER(s.skill) = ? " +
                "AND NOT EXISTS (SELECT 1 FROM offer_mentions m WHERE m.offer_id = s.offer_id AND m.term = ?)", term, term, term);
        } else {
            String word = JobOfferSpecifications.escapeLike(term);
            found = jdbc.update("INSERT INTO offer_mentions (offer_id, term) SELECT o.id, ? FROM job_offers o " +
                "WHERE o.mentions_indexed_at IS NOT NULL AND o.search_text LIKE ? ESCAPE '\\' " +
                "AND (' ' || o.search_text || ' ' LIKE ? ESCAPE '\\' OR ' ' || o.search_text || ' ' LIKE ? ESCAPE '\\') " +
                "AND NOT EXISTS (SELECT 1 FROM offer_mentions m WHERE m.offer_id = o.id AND m.term = ?)",
                term, "%" + word + "%", "% " + word + " %", "% " + word + ". %", term);
        }
        jdbc.update("UPDATE offer_terms SET indexed = TRUE WHERE term = ?", term);
        log.info("Offer term recorded: {} offers mention it", found);
    }

    private Set<String> readIndexed() {
        return new HashSet<>(jdbc.queryForList("SELECT term FROM offer_terms WHERE indexed = TRUE", String.class));
    }

    /** Takes the lock row, created on first use in a transaction of its own (a failed insert spoils the one it is in). */
    private void lock() {
        if (jdbc.queryForList("SELECT term FROM offer_terms WHERE term = ? FOR UPDATE", String.class, LOCK).isEmpty()) {
            try {
                transactions().executeWithoutResult(status ->
                    jdbc.update("INSERT INTO offer_terms (term, indexed, created_at) VALUES (?, FALSE, ?)", LOCK, Timestamp.from(clock.instant())));
            } catch (DataIntegrityViolationException e) {
                log.debug("Another replica created the lock row");
            }
            jdbc.queryForList("SELECT term FROM offer_terms WHERE term = ? FOR UPDATE", String.class, LOCK);
        }
    }

}
