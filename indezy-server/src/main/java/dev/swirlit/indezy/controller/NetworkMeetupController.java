package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.NetworkMeetupDtos;
import dev.swirlit.indezy.mail.MailProperties;
import dev.swirlit.indezy.model.enums.NetworkMeetupAttendance;
import dev.swirlit.indezy.service.NetworkMeetupService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.nio.charset.StandardCharsets;

/** The member network's events: meetups and webinars members organize and answer Going or Interested. */
@RestController
@RequestMapping("/network/events")
@RequiredArgsConstructor
@Tag(name = "Network", description = "The member network of freelancers, joined by choice")
public class NetworkMeetupController {

    private final NetworkMeetupService meetups;
    private final MailProperties properties;

    @Operation(summary = "A page of events, 20 of them", description = "scope UPCOMING (the soonest first), ATTENDING (those answered) or " +
        "ORGANIZED (the reader's, past ones too); tag to those naming it; q to those whose words hold it")
    @GetMapping
    public NetworkMeetupDtos.Meetups list(@RequestParam(defaultValue = "UPCOMING") NetworkMeetupService.Scope scope,
                                          @RequestParam(required = false) String tag, @RequestParam(defaultValue = "") String q,
                                          @RequestParam(defaultValue = "0") int page) {
        return meetups.list(SecurityUtils.getCurrentUserId(), scope, tag, q, page);
    }

    @Operation(summary = "Organize an event, the organizer going", description = "In a place (its address) or online (its link); 429 past 5 a day")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NetworkMeetupDtos.Meetup create(@Valid @RequestBody NetworkMeetupDtos.NewMeetup request) {
        return meetups.create(SecurityUtils.getCurrentUserId(), request);
    }

    @Operation(summary = "One event")
    @GetMapping("/{id}")
    public NetworkMeetupDtos.Meetup get(@PathVariable Long id) {
        return meetups.get(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "Change an event: its organizer or the administrators, until it ends")
    @PutMapping("/{id}")
    public NetworkMeetupDtos.Meetup update(@PathVariable Long id, @Valid @RequestBody NetworkMeetupDtos.NewMeetup request) {
        return meetups.update(SecurityUtils.getCurrentUserId(), id, request);
    }

    @Operation(summary = "Cancel an event: its organizer or the administrators", description = "Those who answered it are told")
    @DeleteMapping("/{id}")
    public NetworkMeetupDtos.Meetup cancel(@PathVariable Long id) {
        return meetups.cancel(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "Answer an event Going or Interested", description = "409 for a cancelled or past event")
    @PutMapping("/{id}/attendance")
    public NetworkMeetupDtos.Meetup attend(@PathVariable Long id, @RequestParam NetworkMeetupAttendance status) {
        return meetups.attend(SecurityUtils.getCurrentUserId(), id, status);
    }

    @Operation(summary = "Take an answer back")
    @DeleteMapping("/{id}/attendance")
    public NetworkMeetupDtos.Meetup unattend(@PathVariable Long id) {
        return meetups.attend(SecurityUtils.getCurrentUserId(), id, null);
    }

    @Operation(summary = "Who answered an event: those going, then those interested")
    @GetMapping("/{id}/attendees")
    public NetworkMeetupDtos.Attendees attendees(@PathVariable Long id) {
        return meetups.attendees(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "The event as an iCalendar file, to add it to a calendar")
    @GetMapping(value = "/{id}/ics", produces = "text/calendar")
    public ResponseEntity<byte[]> ics(@PathVariable Long id) {
        byte[] body = meetups.ics(SecurityUtils.getCurrentUserId(), id, properties.appUrl()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"indezy-event-" + id + ".ics\"").body(body);
    }
}
