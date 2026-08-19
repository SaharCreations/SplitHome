package com.splithome.app.repository;

import com.splithome.app.model.ExpenseShare;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {
    List<ExpenseShare> findByExpenseHouseholdId(Long householdId);
    List<ExpenseShare> findByExpenseId(Long expenseId);
    Optional<ExpenseShare> findByExpenseIdAndMemberId(Long expenseId, Long memberId);
    void deleteByExpenseId(Long expenseId);
}
