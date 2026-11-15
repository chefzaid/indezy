package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.NetworkUseful;
import dev.swirlit.indezy.repository.NetworkUsefulRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** **Useful**, the second reaction (see docs/features.md#member-network): once a member, on a post they can read but their own. */
@Service
@RequiredArgsConstructor
public class NetworkUsefulService {

    private final NetworkPostService posts;
    private final NetworkUsefulRepository usefuls;

    @Transactional
    public NetworkDtos.Post useful(Long userId, Long postId, boolean useful) {
        NetworkPost post = posts.readable(userId, postId);
        if (post.getAuthorId().equals(userId)) {
            throw new IllegalArgumentException("A member does not find their own post useful");
        }
        var existing = usefuls.findByPostIdAndUserId(postId, userId);
        if (useful && existing.isEmpty()) {
            NetworkUseful found = new NetworkUseful();
            found.setPostId(postId);
            found.setUserId(userId);
            usefuls.save(found);
        } else if (!useful) {
            existing.ifPresent(usefuls::delete);
        }
        return posts.reread(post, userId);
    }
}
