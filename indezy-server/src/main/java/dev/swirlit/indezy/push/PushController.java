package dev.swirlit.indezy.push;

import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Web push for the account's browsers: the key to subscribe with, then each browser's subscription. */
@RestController
@RequestMapping("/push")
@RequiredArgsConstructor
@Tag(name = "Web push", description = "Notifications pushed to the browsers that accepted them")
public class PushController {

    private final PushService push;

    @Operation(summary = "The VAPID public key to subscribe with", description = "Null while web push is not set up")
    @GetMapping("/key")
    public PushDtos.Key key() {
        return push.key();
    }

    @Operation(summary = "The events web push tells the account of, each on or off")
    @GetMapping("/events")
    public java.util.Map<PushEvent, Boolean> events() {
        return push.events(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Turn web push events on or off", description = "Those not named stay as they are")
    @org.springframework.web.bind.annotation.PutMapping("/events")
    public java.util.Map<PushEvent, Boolean> setEvents(@RequestBody java.util.Map<PushEvent, Boolean> changes) {
        return push.setEvents(SecurityUtils.getCurrentUserId(), changes);
    }

    @Operation(summary = "Keep this browser's subscription")
    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody PushDtos.Subscription subscription) {
        push.subscribe(SecurityUtils.getCurrentUserId(), subscription);
    }

    @Operation(summary = "Forget this browser's subscription")
    @PostMapping("/subscriptions/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody PushDtos.Unsubscription request) {
        push.unsubscribe(SecurityUtils.getCurrentUserId(), request.endpoint());
    }
}
