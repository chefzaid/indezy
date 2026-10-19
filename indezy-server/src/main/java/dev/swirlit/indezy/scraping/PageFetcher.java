package dev.swirlit.indezy.scraping;

import java.io.IOException;

/**
 * Fetches the pages of one job board for its reader, following the collection rules: only that site's
 * domain, its {@code robots.txt}, a pause between requests and a response size cap.
 */
public interface PageFetcher {

    String get(String url) throws IOException, InterruptedException;

    String postJson(String url, String json) throws IOException, InterruptedException;
}
