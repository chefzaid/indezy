package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.NetworkAvailability;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The member network (see docs/features.md#member-network): joining is each account's choice, with a handle unique among
 * members and a member card built from the profile, each field shown or hidden. Leaving hides the card at once; the
 * handle stays the account's for its return. Only members see members.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkService {

    static final String NOT_FOUND = "No member with this handle";
    static final String BANNED = "The moderators ended this account's membership";
    /** Handles that would pass for Indezy itself, or for a page. */
    static final Set<String> RESERVED = Set.of("admin", "administrator", "indezy", "support", "help", "moderator", "moderation",
        "network", "settings", "system", "root", "null", "undefined", "me", "you", "team", "staff", "official");
    private static final java.util.regex.Pattern HANDLE = java.util.regex.Pattern.compile(NetworkDtos.HANDLE);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final NetworkDtos.Fields ALL_SHOWN = new NetworkDtos.Fields(true, true, true, true, true, true, false);
    private static final NetworkDtos.Availability AUTO = new NetworkDtos.Availability(NetworkAvailability.AUTO, null);

    private final NetworkMemberRepository members;
    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final ProjectRepository projects;
    private final CompanyDirectory directory;
    private final Clock clock;

    @Transactional(readOnly = true)
    public NetworkDtos.Membership membership(Long userId) {
        User user = user(userId);
        return members.findByUserId(userId).filter(NetworkMember::isActive).map(member -> membership(member, user))
            .orElseGet(() -> new NetworkDtos.Membership(false, members.findByUserId(userId).map(NetworkMember::getHandle)
                .orElseGet(() -> suggestion(user)), members.findByUserId(userId).map(NetworkService::fields).orElse(ALL_SHOWN),
                members.findByUserId(userId).map(NetworkService::availability).orElse(AUTO), null,
                members.findByUserId(userId).map(NetworkMember::isPrivate).orElse(false)));
    }

    /** Joins with the handle and the fields chosen, or changes them; a handle another member holds is refused. */
    @Transactional
    public NetworkDtos.Membership join(Long userId, NetworkDtos.Join request) {
        User user = user(userId);
        String handle = request.handle();
        if (!available(userId, handle)) {
            throw new InvalidStateException("The handle @" + handle + " is already taken");
        }
        NetworkMember member = members.findByUserId(userId).orElseGet(NetworkMember::new);
        if (member.getBannedAt() != null) {
            throw new InvalidStateException(BANNED);
        }
        if (member.getId() == null || !member.isActive()) {
            member.setJoinedAt(clock.instant());
            log.info("Account {} joined the network", userId);
        }
        member.setUserId(userId);
        member.setHandle(handle);
        member.setActive(true);
        NetworkDtos.Fields fields = request.fields();
        member.setShowPhoto(fields.photo());
        member.setShowHeadline(fields.headline());
        member.setShowStack(fields.stack());
        member.setShowLocation(fields.location());
        member.setShowAvailability(fields.availability());
        member.setShowLanguages(fields.languages());
        member.setRemote(fields.remote());
        NetworkDtos.Availability availability = Objects.requireNonNullElse(request.availability(), AUTO);
        member.setAvailabilityMode(availability.mode());
        boolean dated = availability.mode() == NetworkAvailability.AVAILABLE || availability.mode() == NetworkAvailability.ON_MISSION;
        member.setAvailabilityDate(dated ? availability.date() : null);
        return membership(members.save(member), user);
    }

    /** Leaves: the card is no longer shown to anyone. */
    @Transactional
    public void leave(Long userId) {
        members.findByUserId(userId).filter(NetworkMember::isActive).ifPresent(member -> {
            member.setActive(false);
            log.info("Account {} left the network", userId);
        });
    }

    /** Whether the account can take the handle: well formed, not reserved, and no other member's. */
    @Transactional(readOnly = true)
    public NetworkDtos.HandleAvailability availability(Long userId, String handle) {
        String normalized = handle == null ? "" : handle.strip().toLowerCase(Locale.ROOT);
        return new NetworkDtos.HandleAvailability(normalized, available(userId, normalized));
    }

    /** A member's card, for members only; a former member, like an unknown handle, is not found. */
    @Transactional(readOnly = true)
    public NetworkDtos.MemberCard card(Long viewerId, String handle) {
        requireMember(viewerId);
        return card(activeMember(handle));
    }

    /** The viewer's own membership, refused until the account joins: only members see members. */
    NetworkMember requireMember(Long userId) {
        return members.findByUserId(userId).filter(NetworkMember::isActive)
            .orElseThrow(() -> new InvalidStateException("Join the network to see its members"));
    }

    /** The member holding the handle, still in the network and with an account; not found otherwise. */
    NetworkMember activeMember(String handle) {
        return members.findByHandle(handle.toLowerCase(Locale.ROOT)).filter(NetworkMember::isActive)
            .filter(member -> users.findById(member.getUserId()).filter(owner -> owner.getDeletedAt() == null).isPresent())
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /** The member's card as members see it. */
    NetworkDtos.MemberCard card(NetworkMember member) {
        return card(member, user(member.getUserId()));
    }

    /** The member's card, unless the account is gone or deleted. */
    Optional<NetworkDtos.MemberCard> liveCard(NetworkMember member) {
        return users.findById(member.getUserId()).filter(owner -> owner.getDeletedAt() == null).map(owner -> card(member, owner));
    }

    private boolean available(Long userId, String handle) {
        return HANDLE.matcher(handle).matches() && !RESERVED.contains(handle) &&
            members.findByHandle(handle).filter(holder -> !holder.getUserId().equals(userId)).isEmpty();
    }

    /** The first name as a handle, then with the last name's initial or a number until one is free. */
    private String suggestion(User user) {
        String first = plain(user.getFirstName());
        String base = first.length() >= 3 && Character.isLetter(first.charAt(0)) ? first : "member" + first;
        String last = plain(user.getLastName());
        return Stream.concat(Stream.of(base, last.isEmpty() ? base : base + "_" + last.charAt(0)),
                Stream.iterate(2, number -> number + 1).limit(98).map(number -> base + number))
            .map(candidate -> candidate.length() > NetworkMember.HANDLE_MAX ? candidate.substring(0, NetworkMember.HANDLE_MAX) : candidate)
            .filter(candidate -> available(user.getId(), candidate)).findFirst().orElse("member" + user.getId());
    }

    /** Lower-case letters and digits alone, accents dropped ({@code Léa-Marie} reads {@code leamarie}). */
    private static String plain(String name) {
        String stripped = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private NetworkDtos.Membership membership(NetworkMember member, User user) {
        return new NetworkDtos.Membership(true, member.getHandle(), fields(member), availability(member), card(member, user),
            member.isPrivate());
    }

    private static NetworkDtos.Availability availability(NetworkMember member) {
        return new NetworkDtos.Availability(Objects.requireNonNullElse(member.getAvailabilityMode(), NetworkAvailability.AUTO),
            member.getAvailabilityDate());
    }

    private static NetworkDtos.Fields fields(NetworkMember member) {
        return new NetworkDtos.Fields(member.isShowPhoto(), member.isShowHeadline(), member.isShowStack(), member.isShowLocation(),
            member.isShowAvailability(), member.isShowLanguages(), member.isRemote());
    }

    private NetworkDtos.MemberCard card(NetworkMember member, User user) {
        NetworkDtos.Availability shown = member.isShowAvailability() ? shownAvailability(member, user) : null;
        String avatar = user.getAvatarImage() != null ? user.getAvatarImage() : PublicProfileService.webLink(user.getAvatar());
        return new NetworkDtos.MemberCard(member.getHandle(), user.getFirstName(), user.getLastName(), member.isShowPhoto() ? avatar : null,
            member.isShowHeadline() ? user.getPosition() : null, member.isShowStack() ? user.getSkills() : List.of(),
            member.isShowLocation() ? user.getCity() : null, member.isRemote(), shown == null ? null : shown.mode(),
            shown == null ? null : shown.date(), member.isShowLanguages() ? user.getLanguages() : List.of(),
            member.getVerifiedSiret() != null && member.getVerifiedSiret().equals(siret(user).orElse(null)));
    }

    /** The SIRET of the account's billing identity, if it set one. */
    Optional<String> siret(User user) {
        return freelances.findByEmail(user.getEmail()).map(Freelance::getBilling).map(BillingIdentity::getSiret);
    }

    /**
     * Verifies the member against the public registry: their billing SIRET must be an active establishment whose legal
     * unit names them (the entrepreneur of an EI, an executive of a company), first and last name, accents and case aside.
     */
    @Transactional
    public NetworkDtos.Membership verify(Long userId) {
        NetworkMember member = requireMember(userId);
        User user = user(userId);
        String siret = siret(user).orElseThrow(() -> new InvalidStateException("Add your SIRET in Profile → Billing to be verified"));
        CompanyDirectory.Registration registration;
        try {
            registration = directory.registration(siret)
                .filter(found -> found.active() && found.establishmentActive())
                .orElseThrow(() -> new InvalidStateException("The registry has no active establishment with this SIRET"));
        } catch (CompanyDirectory.UnavailableException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
        }
        String first = upper(user.getFirstName());
        String last = upper(user.getLastName());
        if (last.isEmpty() || registration.people().stream().map(NetworkService::upper)
                .noneMatch(person -> person.contains(last) && (first.isEmpty() || person.contains(first)))) {
            throw new InvalidStateException("The registry names someone else for this SIRET");
        }
        member.setVerifiedSiret(siret);
        member.setVerifiedAt(clock.instant());
        log.info("Account {} verified its SIRET for the network", userId);
        return membership(member, user);
    }

    /** Upper-case letters and single spaces, accents dropped, to compare names as the registry writes them. */
    private static String upper(String name) {
        String stripped = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]+", " ").strip();
    }

    /**
     * The availability the card tells: as set, an availability day past reading as now; or, automatic, on the won mission
     * running today until its last day (none said for an open-ended one), else available from the profile's day, else none.
     */
    private NetworkDtos.Availability shownAvailability(NetworkMember member, User user) {
        LocalDate today = LocalDate.now(clock.withZone(PARIS));
        NetworkAvailability mode = Objects.requireNonNullElse(member.getAvailabilityMode(), NetworkAvailability.AUTO);
        if (mode == NetworkAvailability.AVAILABLE) {
            LocalDate from = member.getAvailabilityDate();
            return new NetworkDtos.Availability(mode, from == null || !from.isAfter(today) ? null : from);
        }
        if (mode != NetworkAvailability.AUTO) {
            return new NetworkDtos.Availability(mode, member.getAvailabilityDate());
        }
        Optional<Freelance> freelance = freelances.findByEmail(user.getEmail());
        List<Project> running = freelance.map(owner -> projects.findByFreelanceId(owner.getId())).orElse(List.of()).stream()
            .filter(project -> project.getStatus() == ProjectStatus.WON && project.getStartDate() != null && !project.getStartDate().isAfter(today))
            .filter(project -> project.lastDay() == null || !project.lastDay().isBefore(today)).toList();
        if (!running.isEmpty()) {
            LocalDate until = running.stream().anyMatch(project -> project.lastDay() == null) ? null :
                running.stream().map(Project::lastDay).max(Comparator.naturalOrder()).orElseThrow();
            return new NetworkDtos.Availability(NetworkAvailability.ON_MISSION, until);
        }
        LocalDate from = freelance.map(Freelance::getAvailabilityDate).orElse(null);
        return from == null ? null : new NetworkDtos.Availability(NetworkAvailability.AVAILABLE, from.isAfter(today) ? from : null);
    }

    private User user(Long userId) {
        return users.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));
    }
}
