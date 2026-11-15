package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkMissionTake;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.enums.NetworkPostKind;
import dev.swirlit.indezy.repository.NetworkMissionTakeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** What a mission passed on shows of its takers: how many, to its author only, and whether the reader took it. */
@Component
@RequiredArgsConstructor
public class NetworkMissionTakes {

    private final NetworkMissionTakeRepository takes;

    Map<Long, NetworkDtos.Taken> views(Collection<NetworkPost> shown, Long readerId) {
        List<NetworkPost> missions = shown.stream().filter(post -> post.getKind() == NetworkPostKind.MISSION).toList();
        if (missions.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<NetworkMissionTake>> byPost = takes.findByPostIdIn(missions.stream().map(NetworkPost::getId).toList()).stream()
            .collect(Collectors.groupingBy(NetworkMissionTake::getPostId));
        Map<Long, NetworkDtos.Taken> views = new HashMap<>();
        for (NetworkPost post : missions) {
            List<NetworkMissionTake> taken = byPost.getOrDefault(post.getId(), List.of());
            views.put(post.getId(), new NetworkDtos.Taken(post.getAuthorId().equals(readerId) ? taken.size() : null,
                taken.stream().anyMatch(take -> take.getUserId().equals(readerId))));
        }
        return views;
    }
}
