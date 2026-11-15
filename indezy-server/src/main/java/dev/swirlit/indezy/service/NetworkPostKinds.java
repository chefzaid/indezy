package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkPostDetails;
import dev.swirlit.indezy.model.enums.NetworkPostKind;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The facts each kind of post carries (see docs/features.md#member-network): a mission names its role, an availability
 * its start; a plain post, a question or a retour d'expérience is its text alone.
 */
final class NetworkPostKinds {

    private NetworkPostKinds() {
    }

    /** The facts kept for a post of this kind, checked; none for a kind without. */
    static NetworkPostDetails details(NetworkPostKind kind, NetworkDtos.PostDetails given) {
        if (kind != NetworkPostKind.MISSION && kind != NetworkPostKind.AVAILABLE) {
            return null;
        }
        if (given == null) {
            throw new IllegalArgumentException(kind == NetworkPostKind.MISSION ? "A mission names its role" : "An availability names its start");
        }
        String role = blankToNull(given.role());
        if (kind == NetworkPostKind.MISSION && role == null) {
            throw new IllegalArgumentException("A mission names its role");
        }
        if (kind == NetworkPostKind.AVAILABLE && given.start() == null) {
            throw new IllegalArgumentException("An availability names its start");
        }
        if (given.rateMin() != null && given.rateMax() != null && given.rateMin() > given.rateMax()) {
            throw new IllegalArgumentException("The lowest rate comes first");
        }
        NetworkPostDetails details = new NetworkPostDetails();
        details.setRole(role);
        details.setRateMin(given.rateMin());
        details.setRateMax(given.rateMax());
        details.setCity(blankToNull(given.city()));
        details.setRemoteDays(given.remoteDays());
        details.setStart(given.start());
        details.setClient(kind == NetworkPostKind.MISSION ? blankToNull(given.client()) : null);
        String stack = String.join(",", given.stack().stream().map(String::strip).distinct().toList());
        details.setStack(stack.isEmpty() ? null : stack);
        return details;
    }

    static NetworkDtos.PostDetails toDto(NetworkPostDetails details) {
        if (details == null) {
            return null;
        }
        List<String> stack = details.getStack() == null ? List.of() : Arrays.stream(details.getStack().split(",")).filter(skill -> !skill.isBlank())
            .toList();
        return new NetworkDtos.PostDetails(details.getRole(), details.getRateMin(), details.getRateMax(), details.getCity(),
            details.getRemoteDays(), details.getStart(), details.getClient(), stack);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : Objects.requireNonNull(text).strip();
    }
}
