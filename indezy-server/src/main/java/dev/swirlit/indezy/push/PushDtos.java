package dev.swirlit.indezy.push;

import dev.swirlit.indezy.model.PushSubscription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What the browser sends when it accepts web push, as {@code PushSubscription.toJSON()} gives it. */
public final class PushDtos {

    private PushDtos() {
    }

    /** The server's VAPID public key the browser subscribes with; null while push is not set up. */
    public record Key(String publicKey) {
    }

    public record Keys(
        @NotBlank @Size(max = 120) @Pattern(regexp = "[A-Za-z0-9_-]+=*") String p256dh,
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+=*") String auth) {
    }

    public record Subscription(@NotBlank @Size(max = PushSubscription.ENDPOINT_MAX) String endpoint, @NotNull @Valid Keys keys) {

        // The endpoint and the keys stay out of logs.
        @Override
        public String toString() {
            return "Subscription[]";
        }
    }

    public record Unsubscription(@NotBlank @Size(max = PushSubscription.ENDPOINT_MAX) String endpoint) {

        @Override
        public String toString() {
            return "Unsubscription[]";
        }
    }
}
