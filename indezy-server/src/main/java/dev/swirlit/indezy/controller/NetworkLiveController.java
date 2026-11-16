package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.NetworkMessageDtos;
import dev.swirlit.indezy.service.NetworkLiveService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/network")
@RequiredArgsConstructor
@Tag(name = "Network live", description = "What changes in the member network as it happens")
public class NetworkLiveController {

    private final NetworkLiveService live;

    @Operation(summary = "The member's stream of what changed (server-sent events)",
        description = "Events: ready, message and read (with the conversation), typing (with the conversation and a first name), " +
            "notice; a comment every 25 seconds; closed after 15 minutes, when the page opens another")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) {
        // Proxies pass each event on as it comes rather than holding the response.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return live.stream(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Tell the conversation's other members the reader is typing", description = "Sent every few seconds while typing")
    @PostMapping("/conversations/{id}/typing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void typing(@PathVariable Long id) {
        live.typing(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "Whether the member shows and sees read receipts and who is typing")
    @GetMapping("/messages/settings")
    public NetworkMessageDtos.Messaging messaging() {
        return live.messaging(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Show or hide read receipts and who is typing, both ways")
    @PutMapping("/messages/settings")
    public NetworkMessageDtos.Messaging saveMessaging(@RequestBody NetworkMessageDtos.Messaging settings) {
        return live.saveMessaging(SecurityUtils.getCurrentUserId(), settings);
    }
}
