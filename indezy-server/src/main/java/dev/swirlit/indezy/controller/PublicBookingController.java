package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.BookingDtos;
import dev.swirlit.indezy.service.BookingService;
import dev.swirlit.indezy.util.ClientAddress;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What a booking link shows a recruiter, without an account; an unknown or closed link answers 404. */
@RestController
@RequestMapping("/public/booking")
@RequiredArgsConstructor
@Tag(name = "Booking", description = "Book a call with a freelancer through their link")
public class PublicBookingController {

    private final BookingService bookings;

    @Operation(summary = "The free starts of the link, day by day")
    @GetMapping("/{slug}")
    public ResponseEntity<BookingDtos.PublicPage> page(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(bookings.publicPage(slug));
    }

    @Operation(summary = "Book a free start")
    @PostMapping("/{slug}")
    public ResponseEntity<BookingDtos.Confirmation> book(@PathVariable String slug, @Valid @RequestBody BookingDtos.Request request,
                                                         HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookings.book(slug, request, ClientAddress.of(http)));
    }
}
