package com.wanderly.api.repository;

import com.wanderly.api.domain.Destination;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DestinationRepository extends JpaRepository<Destination, String> {
}
