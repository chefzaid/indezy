package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkTagFollow;
import dev.swirlit.indezy.repository.NetworkTagFollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tags of the member network (see docs/features.md#member-network): a tag's page, with its posts and the members whose
 * cards show the skill, and the tags a member follows, whose posts reach their home feed.
 */
@Service
@RequiredArgsConstructor
public class NetworkTagService {

    static final int MEMBERS_SHOWN = 12;
    private static final Pattern TAG = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}_-]{0,39}");

    private final NetworkService network;
    private final NetworkPostService posts;
    private final NetworkDirectoryService directory;
    private final NetworkTagFollowRepository follows;

    @Transactional(readOnly = true)
    public NetworkDtos.TagPage page(Long userId, String tag, int page) {
        String name = normalized(tag);
        List<NetworkDtos.MemberCard> members = directory.search(userId, new NetworkDirectoryService.Search(name, null, null, null, 0))
            .members().stream().limit(MEMBERS_SHOWN).toList();
        return new NetworkDtos.TagPage(name, follows.findByUserIdAndTag(userId, name).isPresent(), posts.tagPosts(userId, name, page), members);
    }

    /** Follows a tag, or no longer; answers the tags the member follows, in order. */
    @Transactional
    public List<String> follow(Long userId, String tag, boolean followed) {
        network.requireMember(userId);
        String name = normalized(tag);
        var existing = follows.findByUserIdAndTag(userId, name);
        if (followed && existing.isEmpty()) {
            NetworkTagFollow follow = new NetworkTagFollow();
            follow.setUserId(userId);
            follow.setTag(name);
            follows.save(follow);
        } else if (!followed) {
            existing.ifPresent(follows::delete);
        }
        return followed(userId);
    }

    @Transactional(readOnly = true)
    public List<String> followed(Long userId) {
        network.requireMember(userId);
        return follows.findByUserIdOrderByTagAsc(userId).stream().map(NetworkTagFollow::getTag).toList();
    }

    /** The tag as posts keep it: lower-case, without the #; refused when it is not one. */
    static String normalized(String tag) {
        String name = tag == null ? "" : tag.strip().replaceFirst("^#", "").toLowerCase(Locale.ROOT);
        if (!TAG.matcher(name).matches()) {
            throw new IllegalArgumentException("Not a tag: letters, digits, dashes or underscores, 40 at most");
        }
        return name;
    }
}
