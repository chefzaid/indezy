package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPageView;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkPageViewRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * **Who viewed my page** (see docs/features.md#member-network): a member opening another's page is kept once a day per
 * pair, 90 days long; the member reads who did, the latest first, but those blocked either way, away from the network or
 * browsing privately (counted without a name). A member browsing privately sees no names either.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkPageViewService {

    /** How long a view is kept, and read. */
    static final Duration KEPT = Duration.ofDays(90);
    static final Duration WEEK = Duration.ofDays(7);
    /** How many viewers are named at most. */
    static final int NAMED = 50;

    private final NetworkService network;
    private final NetworkFollowService follows;
    private final NetworkMemberRepository members;
    private final NetworkPageViewRepository views;
    private final NetworkRestrictionRepository restrictions;
    private final Clock clock;

    /** The viewer opened the member's page; their own page, or one hidden from them, counts nothing. */
    @Transactional
    public void record(Long viewerId, String handle) {
        NetworkMember viewer = network.requireMember(viewerId);
        NetworkMember member = follows.visible(viewer, handle);
        if (member.getUserId().equals(viewerId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        NetworkPageView view = views.findByViewerIdAndMemberIdAndViewedOn(viewerId, member.getUserId(), now.toLocalDate())
            .orElseGet(() -> {
                NetworkPageView fresh = new NetworkPageView();
                fresh.setViewerId(viewerId);
                fresh.setMemberId(member.getUserId());
                fresh.setViewedOn(now.toLocalDate());
                return fresh;
            });
        view.setViewedAt(now);
        views.save(view);
    }

    /** Who viewed the reader's page in the last 90 days. */
    @Transactional(readOnly = true)
    public NetworkDtos.PageViews viewers(Long userId) {
        NetworkMember reader = network.requireMember(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(userId));
        Map<Long, LocalDateTime> latest = new LinkedHashMap<>();
        int thisWeek = 0;
        for (NetworkPageView view : views.findByMemberIdAndViewedAtAfterOrderByViewedAtDesc(userId, now.minus(KEPT))) {
            if (blocked.contains(view.getViewerId())) {
                continue;
            }
            latest.putIfAbsent(view.getViewerId(), view.getViewedAt());
            if (view.getViewedAt().isAfter(now.minus(WEEK))) {
                thisWeek++;
            }
        }
        List<NetworkDtos.PageViewer> named = new ArrayList<>();
        int anonymous = 0;
        int counted = 0;
        for (Map.Entry<Long, LocalDateTime> entry : latest.entrySet()) {
            Optional<NetworkMember> viewer = members.findByUserId(entry.getKey()).filter(NetworkMember::isActive);
            Optional<NetworkDtos.MemberCard> card = viewer.flatMap(network::liveCard);
            if (card.isEmpty()) {
                continue;
            }
            counted++;
            if (reader.viewsPrivately() || viewer.get().viewsPrivately()) {
                anonymous++;
            } else if (named.size() < NAMED) {
                named.add(new NetworkDtos.PageViewer(card.get(), entry.getValue()));
            }
        }
        return new NetworkDtos.PageViews(counted, thisWeek, named, anonymous, reader.viewsPrivately());
    }

    /** Browses privately, or no longer. */
    @Transactional
    public NetworkDtos.PageViews setPrivateViewing(Long userId, boolean on) {
        NetworkMember member = network.requireMember(userId);
        member.setPrivateViewing(on);
        members.save(member);
        return viewers(userId);
    }

    /** Every night, the views older than 90 days go. */
    @Scheduled(cron = "0 50 3 * * *", zone = "Europe/Paris")
    @Transactional
    public void dropOld() {
        int dropped = views.deleteViewedBefore(LocalDateTime.now(clock).minus(KEPT));
        if (dropped > 0) {
            log.info("Dropped {} page views older than 90 days", dropped);
        }
    }
}
