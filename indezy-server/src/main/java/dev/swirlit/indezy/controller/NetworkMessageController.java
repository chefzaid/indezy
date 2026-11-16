package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.NetworkMessageDtos;
import dev.swirlit.indezy.model.enums.NetworkReaction;
import dev.swirlit.indezy.service.NetworkConversationService;
import dev.swirlit.indezy.service.NetworkMessageService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Conversations between members and their messages, open only towards a member who follows the sender. */
@RestController
@RequestMapping("/network")
@RequiredArgsConstructor
@Tag(name = "Network messages", description = "One-to-one conversations between members")
public class NetworkMessageController {

    private final NetworkConversationService conversations;
    private final NetworkMessageService messages;

    @Operation(summary = "The reader's conversations, the latest message first; the archived ones when asked")
    @GetMapping("/conversations")
    public List<NetworkMessageDtos.Conversation> list(@RequestParam(defaultValue = "false") boolean archived) {
        return conversations.list(SecurityUtils.getCurrentUserId(), archived);
    }

    @Operation(summary = "Open the conversation with a member, or find the one open",
        description = "409 when the member does not follow the reader, either blocks the other, or the day's limit is reached")
    @PostMapping("/conversations")
    public NetworkMessageDtos.Conversation open(@Valid @RequestBody NetworkMessageDtos.Open request) {
        return conversations.open(SecurityUtils.getCurrentUserId(), request.handle());
    }

    @Operation(summary = "How many conversations have unread messages, the muted aside")
    @GetMapping("/conversations/unread")
    public NetworkMessageDtos.Unread unread() {
        return conversations.unread(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "A conversation with 30 of its messages before the given one (the latest with none), the oldest first")
    @GetMapping("/conversations/{id}")
    public NetworkMessageDtos.Thread thread(@PathVariable Long id, @RequestParam(required = false) Long before) {
        return messages.thread(SecurityUtils.getCurrentUserId(), id, before);
    }

    @Operation(summary = "Mark a conversation read up to now")
    @PutMapping("/conversations/{id}/read")
    public NetworkMessageDtos.Conversation read(@PathVariable Long id) {
        return conversations.read(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "Mute or archive a conversation, for the reader alone")
    @PutMapping("/conversations/{id}/settings")
    public NetworkMessageDtos.Conversation settings(@PathVariable Long id, @RequestBody NetworkMessageDtos.Settings settings) {
        return conversations.settings(SecurityUtils.getCurrentUserId(), id, settings);
    }

    @Operation(summary = "Send a message", description = "409 with the reason once the other member no longer follows the sender")
    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public NetworkMessageDtos.Message send(@PathVariable Long id, @Valid @RequestBody NetworkMessageDtos.NewMessage request) {
        return messages.send(SecurityUtils.getCurrentUserId(), id, request);
    }

    @Operation(summary = "Edit one's message within 15 minutes of sending it")
    @PutMapping("/messages/{id}")
    public NetworkMessageDtos.Message edit(@PathVariable Long id, @Valid @RequestBody NetworkMessageDtos.EditedMessage request) {
        return messages.edit(SecurityUtils.getCurrentUserId(), id, request);
    }

    @Operation(summary = "Delete one's message for both sides within 15 minutes of sending it")
    @DeleteMapping("/messages/{id}")
    public NetworkMessageDtos.Message delete(@PathVariable Long id) {
        return messages.delete(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "React to a message, one reaction each, the latest kept")
    @PutMapping("/messages/{id}/reaction")
    public NetworkMessageDtos.Message react(@PathVariable Long id, @RequestParam(defaultValue = "LIKE") NetworkReaction reaction) {
        return messages.react(SecurityUtils.getCurrentUserId(), id, reaction);
    }

    @Operation(summary = "Take one's reaction to a message back")
    @DeleteMapping("/messages/{id}/reaction")
    public NetworkMessageDtos.Message unreact(@PathVariable Long id) {
        return messages.react(SecurityUtils.getCurrentUserId(), id, null);
    }
}
