package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.NetworkInvitationDtos;
import dev.swirlit.indezy.service.NetworkInvitationService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Invitations to the member network, emailed by a member to one of their contacts. */
@RestController
@RequestMapping("/network/invitations")
@RequiredArgsConstructor
@Tag(name = "Network", description = "The member network of freelancers, joined by choice")
public class NetworkInvitationController {

    private final NetworkInvitationService invitations;

    @Operation(summary = "Where one of the reader's contacts stands toward the network",
        description = "NO_EMAIL, INVITABLE, INVITED (with when), MEMBER (with their handle) or DECLINED")
    @GetMapping("/contacts/{contactId}")
    public NetworkInvitationDtos.ContactStanding standing(@PathVariable Long contactId) {
        return invitations.standing(SecurityUtils.getCurrentUserId(), contactId);
    }

    @Operation(summary = "Email one of the reader's contacts an invitation to the network, once",
        description = "409 for a contact without an address, already a member, invited or who declined; 429 past 20 a day")
    @PostMapping("/contacts/{contactId}")
    public NetworkInvitationDtos.ContactStanding invite(@PathVariable Long contactId) {
        return invitations.invite(SecurityUtils.getCurrentUserId(), contactId);
    }
}
