package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Folds the text of the posts saved before the search compared folded texts, once, when the server is ready. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NetworkSearchTextBackfill {

    private final NetworkPostRepository posts;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void foldExistingPosts() {
        int folded = 0;
        for (List<NetworkPost> batch = posts.findTop500BySearchTextIsNull(); !batch.isEmpty(); batch = posts.findTop500BySearchTextIsNull()) {
            batch.forEach(post -> post.setSearchText(NetworkPost.fold(post.getText())));
            posts.saveAllAndFlush(batch);
            folded += batch.size();
        }
        if (folded > 0) {
            log.info("Folded the text of {} posts for the search", folded);
        }
    }
}
