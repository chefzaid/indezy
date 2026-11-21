package dev.swirlit.indezy.push;

import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.mail.MailProperties;
import dev.swirlit.indezy.model.PushSubscription;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.PushSubscriptionRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Web push to the browsers of an account that accepted it (docs/features.md#web-push): each message encrypted for the
 * browser (RFC 8291) and signed with Indezy's VAPID key (RFC 8292), sent in the background after the change that
 * caused it. Only the push services of the major browsers are reached, so a subscription cannot make the server call any
 * other address; one the push service no longer knows (404, 410) is dropped. Without VAPID keys nothing is sent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    /** The push services of Chrome, Edge, Firefox and Safari. */
    static final Set<String> PUSH_HOSTS = Set.of("fcm.googleapis.com", "updates.push.services.mozilla.com", "web.push.apple.com");
    static final List<String> PUSH_HOST_SUFFIXES = List.of(".notify.windows.com", ".push.apple.com");
    static final Duration TTL = Duration.ofDays(1);
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int GONE = 410;
    private static final int NOT_FOUND = 404;
    private static final int MAX_PER_ACCOUNT = 10;

    private final PushProperties properties;
    private final PushSubscriptionRepository subscriptions;
    private final UserRepository users;
    /** Spring's own executor, by its bean name. */
    private final TaskExecutor applicationTaskExecutor;
    private final JsonMapper json;
    private final Clock clock;
    private final MailProperties mail;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public PushDtos.Key key() {
        return new PushDtos.Key(properties.configured() ? properties.vapidPublicKey() : null);
    }

    /** Keeps the browser's subscription for the account; the same browser moving to another account moves with it. */
    @Transactional
    public void subscribe(Long userId, PushDtos.Subscription request) {
        if (!properties.configured()) {
            throw new InvalidStateException("Web push is not set up");
        }
        if (!allowed(request.endpoint())) {
            throw new IllegalArgumentException("Not the push service of a known browser");
        }
        PushSubscription subscription = subscriptions.findByEndpoint(request.endpoint()).orElseGet(PushSubscription::new);
        if (subscription.getId() == null && subscriptions.findByUserId(userId).size() >= MAX_PER_ACCOUNT) {
            throw new IllegalArgumentException("Push is on in " + MAX_PER_ACCOUNT + " browsers already: turn it off in one first");
        }
        subscription.setUserId(userId);
        subscription.setEndpoint(request.endpoint());
        subscription.setP256dh(request.keys().p256dh());
        subscription.setAuth(request.keys().auth());
        subscriptions.save(subscription);
    }

    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        subscriptions.deleteByUserIdAndEndpoint(userId, endpoint);
    }

    /** Whether the server may send to the address: https on the push service of a known browser. */
    static boolean allowed(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equals(uri.getScheme()) && uri.getPort() == -1 && uri.getUserInfo() == null &&
                (PUSH_HOSTS.contains(host) || PUSH_HOST_SUFFIXES.stream().anyMatch(host::endsWith));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Tells the accounts' browsers, in each account's language, with the page a click opens. */
    public void notify(Collection<Long> userIds, Map<DocumentLanguage, String[]> texts, String url) {
        if (!properties.configured() || userIds.isEmpty()) {
            return;
        }
        List<Long> recipients = List.copyOf(userIds);
        applicationTaskExecutor.execute(() -> recipients.forEach(userId -> {
            DocumentLanguage language = users.findById(userId).map(User::getLanguagePreference)
                .map(preference -> preference != null && preference.toLowerCase(Locale.ROOT).startsWith("en") ? DocumentLanguage.EN :
                    DocumentLanguage.FR).orElse(DocumentLanguage.FR);
            String[] text = texts.getOrDefault(language, texts.get(DocumentLanguage.FR));
            subscriptions.findByUserId(userId).forEach(subscription -> send(subscription, text[0], text[1], url));
        }));
    }

    void send(PushSubscription subscription, String title, String body, String url) {
        try {
            byte[] payload = json.writeValueAsBytes(Map.of("notification", Map.of("title", title, "body", body,
                "icon", "assets/brand/icon-192.png", "tag", url,
                "data", Map.of("onActionClick", Map.of("default", Map.of("operation", "navigateLastFocusedOrOpen", "url", url))))));
            URI endpoint = URI.create(subscription.getEndpoint());
            String audience = endpoint.getScheme() + "://" + endpoint.getHost();
            String token = WebPushCrypto.vapidToken(audience, mail.appUrl(), clock.instant().plus(TOKEN_LIFETIME).getEpochSecond(),
                properties.vapidPrivateKey(), properties.vapidPublicKey());
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(TIMEOUT)
                .header("Content-Encoding", "aes128gcm").header("Content-Type", "application/octet-stream")
                .header("TTL", String.valueOf(TTL.toSeconds())).header("Urgency", "normal")
                .header("Authorization", "vapid t=" + token + ", k=" + properties.vapidPublicKey())
                .POST(HttpRequest.BodyPublishers.ofByteArray(WebPushCrypto.encrypt(payload, subscription.getP256dh(), subscription.getAuth())))
                .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == GONE || status == NOT_FOUND) {
                subscriptions.deleteByEndpoint(subscription.getEndpoint());
                log.info("Dropped push subscription {} the push service no longer knows", subscription.getId());
            } else if (status >= 300) {
                log.warn("Push service answered {} for subscription {}", status, subscription.getId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            log.warn("Push to subscription {} failed: {}", subscription.getId(), e.getClass().getSimpleName());
        }
    }
}
