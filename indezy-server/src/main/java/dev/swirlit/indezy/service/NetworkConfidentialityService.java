package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.util.TextFolding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The confidentiality check before a post or a comment goes out (see docs/features.md#member-network): the names of the
 * workspace's clients, ESNs and contacts found in the text, accents and case aside and as whole words, each with what it
 * can be replaced by. It only reads: the member decides to anonymize or to publish anyway.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NetworkConfidentialityService {

    /** Shorter names match too many words to be told. */
    static final int MIN_NAME = 3;

    private final NetworkService network;
    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final ClientRepository clients;
    private final ContactRepository contacts;

    public List<NetworkDtos.ConfidentialMatch> check(Long userId, String text) {
        network.requireMember(userId);
        String email = users.findById(userId).map(User::getEmail).orElse(null);
        Long workspace = email == null ? null : freelances.findByEmail(email).map(freelance -> freelance.getId()).orElse(null);
        if (workspace == null || text == null || text.isBlank()) {
            return List.of();
        }
        String folded = foldKeepingLength(text);
        Map<String, NetworkDtos.ConfidentialMatch> found = new LinkedHashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        for (Client client : clients.findByFreelanceId(workspace)) {
            candidates.add(new Candidate(client.getCompanyName(), Boolean.FALSE.equals(client.getIsFinal()) ?
                NetworkDtos.ConfidentialKind.ESN : NetworkDtos.ConfidentialKind.CLIENT, client.getCity()));
        }
        for (Contact contact : contacts.findByFreelanceId(workspace)) {
            candidates.add(new Candidate(contact.getFullName(), NetworkDtos.ConfidentialKind.CONTACT, null));
        }
        // The longest names first, so "Société Générale Assurances" is told before "Société Générale".
        candidates.sort(Comparator.comparingInt((Candidate candidate) -> candidate.name() == null ? 0 : candidate.name().length()).reversed());
        boolean[] taken = new boolean[text.length()];
        for (Candidate candidate : candidates) {
            String name = candidate.name() == null ? "" : candidate.name().strip();
            if (name.length() < MIN_NAME) {
                continue;
            }
            String wanted = foldKeepingLength(name);
            for (int at = folded.indexOf(wanted); at >= 0; at = folded.indexOf(wanted, at + 1)) {
                int end = at + wanted.length();
                if (wholeWord(folded, at, end) && !taken[at]) {
                    for (int i = at; i < end; i++) {
                        taken[i] = true;
                    }
                    String written = text.substring(at, end);
                    found.putIfAbsent(written, new NetworkDtos.ConfidentialMatch(written, candidate.kind(), candidate.city()));
                }
            }
        }
        return List.copyOf(found.values());
    }

    private static boolean wholeWord(String text, int start, int end) {
        return (start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1))) &&
            (end == text.length() || !Character.isLetterOrDigit(text.charAt(end)));
    }

    /** Folded letter by letter, so that a position in it is the same position in the text. */
    static String foldKeepingLength(String text) {
        StringBuilder folded = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            String one = TextFolding.fold(String.valueOf(text.charAt(i)));
            folded.append(Objects.requireNonNull(one).length() == 1 ? one.charAt(0) : Character.toLowerCase(text.charAt(i)));
        }
        return folded.toString();
    }

    private record Candidate(String name, NetworkDtos.ConfidentialKind kind, String city) {
    }
}
