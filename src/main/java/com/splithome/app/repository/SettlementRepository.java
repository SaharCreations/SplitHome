package com.splithome.app.repository;

import com.splithome.app.model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findByHouseholdIdOrderByCreatedAtDesc(Long householdId);
    List<Settlement> findByExpenseId(Long expenseId);
    void deleteByExpenseId(Long expenseId);
}
