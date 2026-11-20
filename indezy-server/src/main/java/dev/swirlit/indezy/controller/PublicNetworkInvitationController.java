package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.NetworkInvitationDtos;
import dev.swirlit.indezy.service.NetworkInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** An invitation to the member network, read and declined from its link without an account. */
@RestController
@RequestMapping("/public/network-invitations")
@RequiredArgsConstructor
@Tag(name = "Network invitations", description = "The invitations to the member network, from their link")
public class PublicNetworkInvitationController {

    private final NetworkInvitationService invitations;

    @Operation(summary = "Who invites, and whether the address declined every invitation")
    @GetMapping("/{token}")
    public NetworkInvitationDtos.PublicInvitation read(@PathVariable String token) {
        return invitations.read(token);
    }

    @Operation(summary = "Never be invited to the network again", description = "Also the email's one-click unsubscribe (RFC 8058)")
    @PostMapping("/{token}/decline")
    public NetworkInvitationDtos.PublicInvitation decline(@PathVariable String token) {
        return invitations.decline(token);
    }
}
