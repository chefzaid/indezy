package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkNotification;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkNotificationRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import dev.swirlit.indezy.realtime.NetworkEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The network's notification centre (see docs/features.md#member-network): follows, likes, comments, replies, mentions
 * and reposts, kept for their recipient unless they turned that kind off or a block stands between the two, grouped as
 * they read and marked read together.
 */
@Service
@RequiredArgsConstructor
public class NetworkNotificationService {

    static final int READ_BACK = 200;
    static final int GROUPS_SHOWN = 30;
    static final int ACTORS_SHOWN = 3;
    static final int MAX_MENTIONS = 10;
    /** The start of the post a notification is about: enough to tell which, read in full on a wide screen. */
    private static final int EXCERPT = 280;
    /** An @handle not inside a word or an address. */
    private static final Pattern MENTION = Pattern.compile("(?<![\\p{L}\\p{N}_@.])@([a-z][a-z0-9_]{2,29})");
    /** Kinds told once per member and post (or once per member for a follow), whatever happens after. */
    private static final Set<NetworkNotificationType> ONCE = EnumSet.of(NetworkNotificationType.FOLLOW, NetworkNotificationType.FOLLOW_REQUEST,
        NetworkNotificationType.FOLLOW_ACCEPTED, NetworkNotificationType.LIKE,
        NetworkNotificationType.COMMENT_LIKE, NetworkNotificationType.REPOST);
    /** The moderators' word, told whatever the settings, by no member. */
    static final Set<NetworkNotificationType> MODERATION = EnumSet.of(NetworkNotificationType.WARNED,
        NetworkNotificationType.CONTENT_REMOVED, NetworkNotificationType.SUSPENDED, NetworkNotificationType.REPORT_ACTED,
        NetworkNotificationType.REPORT_DISMISSED);

    private final NetworkService network;
    private final NetworkMemberRepository members;
    private final NetworkNotificationRepository notifications;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkPostRepository posts;
    private final NetworkEvents events;

    /** Tells the recipient, unless it is the actor, they are away or turned the kind off, or a block stands between them. */
    void notify(Long recipientId, Long actorId, NetworkNotificationType type, Long postId) {
        if (recipientId == null || recipientId.equals(actorId)) {
            return;
        }
        Optional<NetworkMember> recipient = members.findByUserId(recipientId).filter(NetworkMember::isActive);
        if (recipient.isEmpty() || off(recipient.get()).contains(type) || restrictions.blockedEitherWay(recipientId).contains(actorId)) {
            return;
        }
        if (ONCE.contains(type) && (postId == null ? notifications.existsByRecipientIdAndActorIdAndTypeAndPostIdIsNull(recipientId, actorId, type) :
                notifications.existsByRecipientIdAndActorIdAndTypeAndPostId(recipientId, actorId, type, postId))) {
            return;
        }
        NetworkNotification notification = new NetworkNotification();
        notification.setRecipientId(recipientId);
        notification.setActorId(actorId);
        notification.setType(type);
        notification.setPostId(postId);
        notifications.save(notification);
        events.notice(recipientId);
    }

    /** Tells a member the moderators' decision; kept as by themselves, since no member acted. */
    void moderation(Long recipientId, NetworkNotificationType type) {
        NetworkNotification notification = new NetworkNotification();
        notification.setRecipientId(recipientId);
        notification.setActorId(recipientId);
        notification.setType(type);
        notifications.save(notification);
        events.notice(recipientId);
    }

    /** Tells each member the text mentions by their @handle, up to {@value #MAX_MENTIONS}. */
    void mentions(String text, Long actorId, Long postId) {
        Set<String> handles = new LinkedHashSet<>();
        Matcher matcher = MENTION.matcher(text);
        while (matcher.find() && handles.size() < MAX_MENTIONS) {
            handles.add(matcher.group(1));
        }
        handles.forEach(handle -> members.findByHandle(handle).filter(NetworkMember::isActive)
            .ifPresent(member -> notify(member.getUserId(), actorId, NetworkNotificationType.MENTION, postId)));
    }

    /** The member's notifications, grouped, the newest group first; those of members away or blocked either way left out. */
    @Transactional(readOnly = true)
    public List<NetworkDtos.NotificationGroup> list(Long userId) {
        network.requireMember(userId);
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(userId));
        Map<String, List<NetworkNotification>> groups = new LinkedHashMap<>();
        for (NetworkNotification notification : notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, READ_BACK))) {
            if (!blocked.contains(notification.getActorId())) {
                groups.computeIfAbsent(notification.getType() + ":" + notification.getPostId() + ":" + notification.isRead(), key -> new ArrayList<>())
                    .add(notification);
            }
        }
        Map<Long, Optional<NetworkDtos.MemberCard>> cards = new LinkedHashMap<>();
        Map<Long, String> excerpts = new LinkedHashMap<>();
        return groups.values().stream().map(group -> {
            NetworkNotification latest = group.getFirst();
            if (MODERATION.contains(latest.getType())) {
                return new NetworkDtos.NotificationGroup(latest.getType(), null, null, List.of(), group.size(), latest.getCreatedAt(),
                    latest.isRead());
            }
            List<NetworkDtos.MemberCard> actors = group.stream().map(NetworkNotification::getActorId).distinct()
                .map(actor -> cards.computeIfAbsent(actor, this::card)).flatMap(Optional::stream).toList();
            String excerpt = latest.getPostId() == null ? null : excerpts.computeIfAbsent(latest.getPostId(), this::excerpt);
            return actors.isEmpty() ? null : new NetworkDtos.NotificationGroup(latest.getType(), latest.getPostId(), excerpt,
                actors.stream().limit(ACTORS_SHOWN).toList(), actors.size(), latest.getCreatedAt(), latest.isRead());
        }).filter(Objects::nonNull).limit(GROUPS_SHOWN).toList();
    }

    @Transactional(readOnly = true)
    public long unread(Long userId) {
        network.requireMember(userId);
        return notifications.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId) {
        network.requireMember(userId);
        notifications.markRead(userId);
    }

    @Transactional(readOnly = true)
    public NetworkDtos.NotificationSettings settings(Long userId) {
        NetworkMember member = network.requireMember(userId);
        return new NetworkDtos.NotificationSettings(List.copyOf(off(member)), List.copyOf(emailed(member)));
    }

    /** Turns off the kinds named, and on the others. */
    @Transactional
    public NetworkDtos.NotificationSettings saveSettings(Long userId, NetworkDtos.NotificationSettings settings) {
        NetworkMember member = network.requireMember(userId);
        member.setNotificationsOff(settings.off().stream().filter(type -> !MODERATION.contains(type)).distinct().sorted().map(Enum::name).collect(Collectors.joining(",")));
        if (settings.emailed() != null) {
            // A kind turned off is emailed no more.
            member.setNotificationsEmailed(settings.emailed().stream().filter(type -> !MODERATION.contains(type) && !settings.off().contains(type))
                .distinct().sorted().map(Enum::name).collect(Collectors.joining(",")));
        }
        members.save(member);
        return settings(userId);
    }

    private static Set<NetworkNotificationType> off(NetworkMember member) {
        return kinds(member.getNotificationsOff());
    }

    /** The kinds the member also has emailed, but those turned off. */
    static Set<NetworkNotificationType> emailed(NetworkMember member) {
        Set<NetworkNotificationType> emailed = kinds(member.getNotificationsEmailed());
        emailed.removeAll(kinds(member.getNotificationsOff()));
        return emailed;
    }

    private static Set<NetworkNotificationType> kinds(String names) {
        Set<NetworkNotificationType> types = EnumSet.noneOf(NetworkNotificationType.class);
        if (names != null && !names.isBlank()) {
            Arrays.stream(names.split(",")).map(String::strip).filter(name -> !name.isEmpty())
                .forEach(name -> types.add(NetworkNotificationType.valueOf(name)));
        }
        return types;
    }

    private Optional<NetworkDtos.MemberCard> card(Long userId) {
        return members.findByUserId(userId).filter(NetworkMember::isActive).flatMap(network::liveCard);
    }

    private String excerpt(Long postId) {
        return posts.findById(postId).map(NetworkPost::getText)
            .map(text -> text.length() > EXCERPT ? text.substring(0, EXCERPT - 1) + "…" : text).orElse(null);
    }
}
