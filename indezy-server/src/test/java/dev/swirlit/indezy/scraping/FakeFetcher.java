package dev.swirlit.indezy.scraping;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Answers readers with test resources by URL and records what they asked; unknown URLs answer 404. */
public class FakeFetcher implements PageFetcher {

    private final Map<String, String> pages = new HashMap<>();
    private final List<String> requests = new ArrayList<>();
    private final List<String> postedBodies = new ArrayList<>();

    public FakeFetcher page(String url, String body) {
        pages.put(url, body);
        return this;
    }

    public FakeFetcher resource(String url, String resource) {
        return page(url, read(resource));
    }

    public static String read(String resource) {
        try (InputStream input = FakeFetcher.class.getResourceAsStream("/offers/" + resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing test resource " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String get(String url) throws IOException {
        requests.add(url);
        return answer(url);
    }

    @Override
    public String postJson(String url, String json) throws IOException {
        requests.add(url);
        postedBodies.add(json);
        return answer(url);
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    public List<String> postedBodies() {
        return List.copyOf(postedBodies);
    }

    private String answer(String url) throws SiteResponseException {
        String body = pages.get(url);
        if (body == null) {
            throw new SiteResponseException(404, "GET " + url + " answered 404");
        }
        return body;
    }
}
