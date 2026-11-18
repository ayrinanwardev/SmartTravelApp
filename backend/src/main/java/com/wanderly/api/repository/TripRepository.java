package com.wanderly.api.repository;

import com.wanderly.api.domain.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, String> {
    List<Trip> findByUserIdOrderByStartDateAsc(Long userId);

    Optional<Trip> findByIdAndUserId(String id, Long userId);

    long countByUserId(Long userId);
}
