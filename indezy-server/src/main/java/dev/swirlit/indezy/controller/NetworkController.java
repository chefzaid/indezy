package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.service.NetworkService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The member network: joining, the member card, and the handles. Off until an administrator turns it on. */
@RestController
@RequestMapping("/network")
@RequiredArgsConstructor
@Tag(name = "Network", description = "The member network of freelancers, joined by choice")
public class NetworkController {

    private final NetworkService network;

    @Operation(summary = "Whether the account joined, its handle and what its card shows")
    @GetMapping("/membership")
    public NetworkDtos.Membership membership() {
        return network.membership(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Join the network, or change the handle and the card", description = "409 for a handle another member holds")
    @PutMapping("/membership")
    public NetworkDtos.Membership join(@Valid @RequestBody NetworkDtos.Join request) {
        return network.join(SecurityUtils.getCurrentUserId(), request);
    }

    @Operation(summary = "Leave the network: the card is hidden at once")
    @DeleteMapping("/membership")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave() {
        network.leave(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Whether a handle can be taken")
    @GetMapping("/handles/{handle}")
    public NetworkDtos.HandleAvailability handle(@PathVariable String handle) {
        return network.availability(SecurityUtils.getCurrentUserId(), handle);
    }

    @Operation(summary = "A member's card, for members", description = "409 until the account joins, 404 for a handle no member holds")
    @GetMapping("/members/{handle}")
    public NetworkDtos.MemberCard card(@PathVariable String handle) {
        return network.card(SecurityUtils.getCurrentUserId(), handle);
    }
}
