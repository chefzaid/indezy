package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByFreelanceIdOrderBySpentOnDescIdDesc(Long freelanceId);

    List<Expense> findByFreelanceIdAndSpentOnBetweenOrderBySpentOnDescIdDesc(Long freelanceId, LocalDate from, LocalDate to);

    Optional<Expense> findByIdAndFreelanceId(Long id, Long freelanceId);
}
