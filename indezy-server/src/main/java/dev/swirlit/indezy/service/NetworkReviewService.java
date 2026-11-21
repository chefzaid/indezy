package dev.swirlit.indezy.service;

import dev.swirlit.indezy.admin.AdminProperties;
import dev.swirlit.indezy.dto.NetworkModerationDtos;
import dev.swirlit.indezy.dto.NetworkReviewDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.NetworkReview;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import dev.swirlit.indezy.model.enums.NetworkReportOutcome;
import dev.swirlit.indezy.model.enums.NetworkReviewKind;
import dev.swirlit.indezy.model.enums.NetworkReviewStatus;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.NetworkReviewRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Members' reviews of the clients and ESNs they worked with (see docs/features.md#client-and-esn-reviews): written from
 * one of the author's clients with a won mission, held by {@link NetworkReviewFilter} for an administrator when it looks
 * like spam, an insult, an accusation or personal data, and shown without its author to the members who look at the same
 * company, known by its SIREN, else by its name.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkReviewService {

    static final int SHOWN = 100;
    private static final double TENTHS = 10.0;

    private final NetworkService network;
    private final NetworkNotificationService notices;
    private final NetworkReviewRepository reviews;
    private final ClientRepository clients;
    private final ContactRepository contacts;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final AdminProperties admins;
    private final AccountEventService events;

    /** What the network says of one of the workspace's clients, for a member. */
    @Transactional(readOnly = true)
    public NetworkReviewDtos.Company company(Long userId, Long freelanceId, Long clientId) {
        network.requireMember(userId);
        Client client = client(freelanceId, clientId);
        String key = key(client);
        List<NetworkReview> shown = reviews.findByCompanyKeyAndStatusOrderByUpdatedAtDescIdDesc(key, NetworkReviewStatus.PUBLISHED);
        Optional<NetworkReview> mine = reviews.findByAuthorIdAndCompanyKey(userId, key);
        return new NetworkReviewDtos.Company(client.getCompanyName(), kind(client), shown.size(), average(shown, NetworkReview::getPaymentRating),
            average(shown, NetworkReview::getProcessRating), average(shown, NetworkReview::getRateRating),
            shown.stream().map(review -> toDto(review, userId)).toList(), mine.map(NetworkReviewService::mine).orElse(null),
            worked(freelanceId, clientId));
    }

    /**
     * Writes or rewrites the member's review of one of their clients, which needs a won mission with it: published, or
     * held for an administrator, who is told, as the author is.
     */
    @Transactional
    public NetworkReviewDtos.Company write(Long userId, Long freelanceId, Long clientId, NetworkReviewDtos.Draft draft) {
        network.requireMember(userId);
        Client client = client(freelanceId, clientId);
        if (!worked(freelanceId, clientId)) {
            throw new InvalidStateException("Only a company you won a mission with can be reviewed");
        }
        String key = key(client);
        NetworkReview review = reviews.findByAuthorIdAndCompanyKey(userId, key).orElseGet(NetworkReview::new);
        if (review.getStatus() == NetworkReviewStatus.REMOVED) {
            throw new InvalidStateException("The moderators removed your review of this company");
        }
        String text = draft.text().strip();
        List<String> names = contacts.findByClientId(clientId).stream().flatMap(contact -> names(contact).stream()).toList();
        List<String> others = reviews.findByAuthorId(userId).stream().filter(other -> !Objects.equals(other.getId(), review.getId()))
            .map(NetworkReview::getText).toList();
        Set<NetworkReviewFilter.Reason> reasons = NetworkReviewFilter.reasons(text, names, others);
        review.setAuthorId(userId);
        review.setCompanyKey(key);
        review.setCompanyName(client.getCompanyName());
        review.setClientId(clientId);
        review.setKind(kind(client));
        review.setPaymentRating(draft.paymentRating());
        review.setProcessRating(draft.processRating());
        review.setRateRating(draft.rateRating());
        review.setText(text);
        review.setStatus(reasons.isEmpty() ? NetworkReviewStatus.PUBLISHED : NetworkReviewStatus.HELD);
        review.setHeldReasons(reasons.isEmpty() ? null : reasons.stream().map(Enum::name).collect(Collectors.joining(",")));
        reviews.save(review);
        if (!reasons.isEmpty()) {
            notices.moderation(userId, NetworkNotificationType.REVIEW_HELD);
            administrators().forEach(admin -> notices.moderation(admin, NetworkNotificationType.REVIEW_PENDING));
            log.info("Held review {} of member {}: {}", review.getId(), userId, review.getHeldReasons());
        }
        return company(userId, freelanceId, clientId);
    }

    @Transactional
    public void delete(Long userId, Long freelanceId, Long clientId) {
        network.requireMember(userId);
        Client client = client(freelanceId, clientId);
        NetworkReview review = reviews.findByAuthorIdAndCompanyKey(userId, key(client))
            .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed this company"));
        if (review.getStatus() == NetworkReviewStatus.REMOVED) {
            // Kept, so a removed review cannot be written again under the same company.
            throw new InvalidStateException("The moderators removed your review of this company");
        }
        reviews.delete(review);
    }

    /** A published review a member can read, to report it. */
    @Transactional(readOnly = true)
    public NetworkReview readable(Long userId, Long reviewId) {
        network.requireMember(userId);
        return reviews.findById(reviewId).filter(review -> review.getStatus() == NetworkReviewStatus.PUBLISHED)
            .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
    }

    /** The reviews held, the oldest first, for an administrator. */
    @Transactional(readOnly = true)
    public List<NetworkReviewDtos.Held> held(Function<Long, NetworkModerationDtos.Party> party) {
        return reviews.findByStatusOrderByUpdatedAtAscIdAsc(NetworkReviewStatus.HELD, PageRequest.of(0, SHOWN)).stream()
            .map(review -> new NetworkReviewDtos.Held(review.getId(), party.apply(review.getAuthorId()), review.getCompanyName(),
                review.getKind(), review.getPaymentRating(), review.getProcessRating(), review.getRateRating(), review.getText(),
                review.getHeldReasons() == null ? List.of() : List.of(review.getHeldReasons().split(",")), review.getUpdatedAt()))
            .toList();
    }

    /** Publishes a held review, with its text edited when the administrator gives one, or rejects it; the author is told. */
    @Transactional
    public void release(Long adminId, Long reviewId, NetworkReviewDtos.Release release) {
        NetworkReview review = reviews.findById(reviewId).filter(found -> found.getStatus() == NetworkReviewStatus.HELD)
            .orElseThrow(() -> new ResourceNotFoundException("No held review with this id"));
        if (release.publish()) {
            if (release.text() != null && !release.text().isBlank()) {
                review.setText(release.text().strip());
            }
            review.setStatus(NetworkReviewStatus.PUBLISHED);
            notices.moderation(review.getAuthorId(), NetworkNotificationType.REVIEW_PUBLISHED);
        } else {
            review.setStatus(NetworkReviewStatus.REJECTED);
            notices.moderation(review.getAuthorId(), NetworkNotificationType.REVIEW_REJECTED);
            events.record(review.getAuthorId(), AccountEventType.NETWORK_SANCTIONED, NetworkReportOutcome.REMOVED.name());
        }
        review.setHeldReasons(null);
        log.info("Administrator {} {} review {} of member {}", adminId, release.publish() ? "published" : "rejected", reviewId,
            review.getAuthorId());
    }

    /** Removes a published review after a report. */
    @Transactional
    public void remove(Long reviewId) {
        reviews.findById(reviewId).ifPresent(review -> review.setStatus(NetworkReviewStatus.REMOVED));
    }

    /** The company a client stands for across workspaces: its SIREN, else its name folded. */
    static String key(Client client) {
        String siren = client.getSiren() == null ? "" : client.getSiren().replaceAll("\\s+", "");
        return siren.length() == 9 ? "siren:" + siren : "name:" + NetworkReviewFilter.fold(client.getCompanyName());
    }

    private static NetworkReviewKind kind(Client client) {
        return Boolean.FALSE.equals(client.getIsFinal()) ? NetworkReviewKind.ESN : NetworkReviewKind.CLIENT;
    }

    private Client client(Long freelanceId, Long clientId) {
        return clients.findById(clientId).filter(found -> found.getFreelance() != null && freelanceId.equals(found.getFreelance().getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    /** Whether the workspace won a mission with the client, as its final client or as its ESN. */
    private boolean worked(Long freelanceId, Long clientId) {
        return projects.findByFreelanceId(freelanceId).stream().anyMatch(project -> project.getStatus() == ProjectStatus.WON &&
            (project.getClient() != null && clientId.equals(project.getClient().getId()) ||
                project.getMiddleman() != null && clientId.equals(project.getMiddleman().getId())));
    }

    /** The names a review must not give: the full name and the last name. */
    private static List<String> names(Contact contact) {
        List<String> names = new ArrayList<>();
        String full = (Objects.toString(contact.getFirstName(), "") + " " + Objects.toString(contact.getLastName(), "")).strip();
        if (!full.isEmpty()) {
            names.add(full);
        }
        if (contact.getLastName() != null && !contact.getLastName().isBlank()) {
            names.add(contact.getLastName().strip());
        }
        return names;
    }

    private List<Long> administrators() {
        return admins.emails().isEmpty() ? List.of() :
            users.findByEmailsIgnoringCase(admins.emails()).stream().filter(user -> user.getDeletedAt() == null).map(User::getId).toList();
    }

    private static Double average(List<NetworkReview> shown, ToIntFunction<NetworkReview> rating) {
        return shown.isEmpty() ? null : Math.round(shown.stream().mapToInt(rating).average().orElse(0) * TENTHS) / TENTHS;
    }

    private static NetworkReviewDtos.Review toDto(NetworkReview review, Long readerId) {
        return new NetworkReviewDtos.Review(review.getId(), review.getKind(), review.getPaymentRating(), review.getProcessRating(),
            review.getRateRating(), review.getText(), review.getUpdatedAt(), review.getAuthorId().equals(readerId));
    }

    private static NetworkReviewDtos.Mine mine(NetworkReview review) {
        return new NetworkReviewDtos.Mine(review.getId(), review.getStatus(), review.getPaymentRating(), review.getProcessRating(),
            review.getRateRating(), review.getText(),
            review.getHeldReasons() == null ? List.of() : Stream.of(review.getHeldReasons().split(",")).toList(), review.getUpdatedAt());
    }
}
