package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.BookingDtos;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The workspace's booking link and the calls booked through it. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Booking link", description = "Hours a recruiter can book a call in, and the calls booked")
public class BookingController {

    private final BookingService bookings;
    private final AccessGuard accessGuard;

    @Operation(summary = "The booking link's settings")
    @GetMapping("/users/booking-page")
    public BookingDtos.Page page() {
        return bookings.page(workspace());
    }

    @Operation(summary = "Change the booking link's settings; the first save gives the link its address")
    @PutMapping("/users/booking-page")
    public BookingDtos.Page save(@Valid @RequestBody BookingDtos.Settings settings) {
        return bookings.save(workspace(), settings);
    }

    @Operation(summary = "Give the booking link a new address; the old one stops working")
    @PostMapping("/users/booking-page/new-link")
    public BookingDtos.Page renewLink() {
        return bookings.renewLink(workspace());
    }

    @Operation(summary = "The calls of the last 30 days and to come")
    @GetMapping("/bookings")
    public List<BookingDtos.Booking> list() {
        return bookings.list(workspace());
    }

    @Operation(summary = "Cancel a call")
    @PostMapping("/bookings/{id}/cancel")
    public BookingDtos.Booking cancel(@PathVariable Long id) {
        return bookings.cancel(workspace(), id);
    }

    private Long workspace() {
        return accessGuard.requireWorkspace(null);
    }
}
