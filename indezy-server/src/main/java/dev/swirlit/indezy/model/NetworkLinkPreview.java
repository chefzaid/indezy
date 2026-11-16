package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

/** The card a post's first link shows under it, read once when the post goes out: the site, the title, the description. */
@Embeddable
@Getter
@Setter
public class NetworkLinkPreview {

    @Column(name = "preview_url", length = 2000)
    private String url;

    @Column(name = "preview_site", length = 200)
    private String site;

    @Column(name = "preview_title", length = 200)
    private String title;

    @Column(name = "preview_description", length = 300)
    private String description;
}
