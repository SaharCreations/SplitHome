package com.splithome.app.repository;
import com.splithome.app.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByHouseholdIdOrderByCreatedAtDesc(Long householdId);
    Page<Expense> findByHouseholdId(Long householdId, Pageable pageable);
    Page<Expense> findByHouseholdIdAndDescriptionContainingIgnoreCase(Long householdId, String description, Pageable pageable);
}
