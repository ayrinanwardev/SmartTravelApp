package com.wanderly.api.repository;

import com.wanderly.api.domain.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HotelRepository extends JpaRepository<Hotel, String> {
    List<Hotel> findByDestinationIdOrderByRatingDesc(String destinationId);

    List<Hotel> findAllByOrderByRatingDesc();
}
