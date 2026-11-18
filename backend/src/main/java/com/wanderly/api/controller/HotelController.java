package com.wanderly.api.controller;

import com.wanderly.api.domain.User;
import com.wanderly.api.dto.CommonDtos.BookingDto;
import com.wanderly.api.dto.CommonDtos.BookingRequest;
import com.wanderly.api.dto.CommonDtos.HotelDto;
import com.wanderly.api.service.HotelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class HotelController {

    private final HotelService service;

    public HotelController(HotelService service) {
        this.service = service;
    }

    /** GET /api/hotels?destinationId=bali&sort=price */
    @GetMapping("/hotels")
    public List<HotelDto> list(@RequestParam(required = false) String destinationId,
                               @RequestParam(required = false, defaultValue = "rating") String sort) {
        return service.list(destinationId, "price".equalsIgnoreCase(sort));
    }

    @GetMapping("/hotels/{id}")
    public HotelDto get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping("/bookings")
    public List<BookingDto> myBookings(@AuthenticationPrincipal User user) {
        return service.myBookings(user);
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDto book(@AuthenticationPrincipal User user, @Valid @RequestBody BookingRequest req) {
        return service.book(user, req);
    }

    @PatchMapping("/bookings/{id}/cancel")
    public BookingDto cancel(@AuthenticationPrincipal User user, @PathVariable String id) {
        return service.cancel(user, id);
    }
}
