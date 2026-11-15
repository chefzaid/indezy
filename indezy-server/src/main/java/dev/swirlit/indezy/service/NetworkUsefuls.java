package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.NetworkUseful;
import dev.swirlit.indezy.repository.NetworkUsefulRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** How many members found each post useful, but those blocked either way, and whether the reader did. */
@Component
@RequiredArgsConstructor
public class NetworkUsefuls {

    private final NetworkUsefulRepository usefuls;

    Map<Long, NetworkDtos.Useful> views(Collection<NetworkPost> shown, Long readerId, Set<Long> blocked) {
        if (shown.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<NetworkUseful>> byPost = usefuls.findByPostIdIn(shown.stream().map(NetworkPost::getId).toList()).stream()
            .collect(Collectors.groupingBy(NetworkUseful::getPostId));
        Map<Long, NetworkDtos.Useful> views = new HashMap<>();
        for (NetworkPost post : shown) {
            List<NetworkUseful> found = byPost.getOrDefault(post.getId(), List.of());
            views.put(post.getId(), new NetworkDtos.Useful((int) found.stream().filter(useful -> !blocked.contains(useful.getUserId())).count(),
                found.stream().anyMatch(useful -> useful.getUserId().equals(readerId))));
        }
        return views;
    }
}
