package dev.swirlit.indezy.scraping;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Answers readers with test resources by URL and records what they asked; unknown URLs answer 404, others a set status. */
public class FakeFetcher implements PageFetcher {

    private final Map<String, String> pages = new HashMap<>();
    private final Map<String, Integer> statuses = new HashMap<>();
    private final List<String> requests = new ArrayList<>();
    private final List<String> postedBodies = new ArrayList<>();
    private final Map<String, Map<String, String>> postAnswers = new HashMap<>();

    public FakeFetcher page(String url, String body) {
        statuses.remove(url);
        pages.put(url, body);
        return this;
    }

    /** Has {@code url} answer {@code status}, an error. */
    public FakeFetcher status(String url, int status) {
        pages.remove(url);
        statuses.put(url, status);
        return this;
    }

    /** Has a JSON post to {@code url} whose body holds {@code containing} answer {@code body}, as an API answers by request. */
    public FakeFetcher post(String url, String containing, String body) {
        postAnswers.computeIfAbsent(url, key -> new LinkedHashMap<>()).put(containing, body);
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
        for (Map.Entry<String, String> answer : postAnswers.getOrDefault(url, Map.of()).entrySet()) {
            if (json.contains(answer.getKey())) {
                return answer.getValue();
            }
        }
        return answer(url);
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    public List<String> postedBodies() {
        return List.copyOf(postedBodies);
    }

    private String answer(String url) throws SiteResponseException {
        Integer status = statuses.get(url);
        if (status != null) {
            throw new SiteResponseException(status, "GET " + url + " answered " + status);
        }
        String body = pages.get(url);
        if (body == null) {
            throw new SiteResponseException(404, "GET " + url + " answered 404");
        }
        return body;
    }
}
