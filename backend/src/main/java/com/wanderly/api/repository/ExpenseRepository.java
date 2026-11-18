package com.wanderly.api.repository;

import com.wanderly.api.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, String> {
    List<Expense> findByUserIdOrderByDateDesc(Long userId);

    List<Expense> findByUserIdAndTripIdOrderByDateDesc(Long userId, String tripId);

    Optional<Expense> findByIdAndUserId(String id, Long userId);
}
