package com.splithome.app.service;

import com.splithome.app.dto.Responses.BalanceDto;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SplitHomeServiceTest {
    private SplitHomeService service() {
        return new SplitHomeService(mock(com.splithome.app.repository.HouseholdRepository.class),
                mock(com.splithome.app.repository.MemberRepository.class),
                mock(com.splithome.app.repository.ExpenseRepository.class),
                mock(com.splithome.app.repository.ExpenseShareRepository.class),
                mock(com.splithome.app.repository.SettlementRepository.class));
    }

    @Test void equalSplitPreservesEveryCent() {
        assertEquals(List.of(new BigDecimal("3.34"), new BigDecimal("3.33"), new BigDecimal("3.33")), service().equalShares(new BigDecimal("10.00"), 3));
    }
    @Test void equalSplitWorksForOnePerson() {
        assertEquals(List.of(new BigDecimal("19.99")), service().equalShares(new BigDecimal("19.99"), 1));
    }
    @Test void equalSplitHandlesOneCentRemainderDeterministically() {
        assertEquals(List.of(new BigDecimal("0.01"), new BigDecimal("0.00"), new BigDecimal("0.00")), service().equalShares(new BigDecimal("0.01"), 3));
    }
    @Test void equalSplitRejectsNoParticipants() {
        assertThrows(IllegalArgumentException.class, () -> service().equalShares(new BigDecimal("10.00"), 0));
    }
    @Test void simplifyDebtsProducesMinimalDirectPaymentsForSimpleCase() {
        var debts = service().simplifyDebts(List.of(
                new BalanceDto(1L, "A", new BigDecimal("60.00")),
                new BalanceDto(2L, "B", new BigDecimal("-20.00")),
                new BalanceDto(3L, "C", new BigDecimal("-40.00"))));
        assertEquals(2, debts.size());
        assertTrue(debts.stream().allMatch(d -> d.toMemberId().equals(1L)));
        assertEquals(new BigDecimal("60.00"), debts.stream().map(d -> d.amount()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    @Test void simplifyDebtsIgnoresSettledMembers() {
        var debts = service().simplifyDebts(List.of(
                new BalanceDto(1L, "A", new BigDecimal("0.00")),
                new BalanceDto(2L, "B", new BigDecimal("25.00")),
                new BalanceDto(3L, "C", new BigDecimal("-25.00"))));
        assertEquals(1, debts.size());
        assertEquals(new BigDecimal("25.00"), debts.get(0).amount());
    }
    @Test void simplifyDebtsPreservesTotalTransferredAcrossMultipleCreditors() {
        var debts = service().simplifyDebts(List.of(
                new BalanceDto(1L, "A", new BigDecimal("30.00")),
                new BalanceDto(2L, "B", new BigDecimal("20.00")),
                new BalanceDto(3L, "C", new BigDecimal("-50.00"))));
        assertEquals(new BigDecimal("50.00"), debts.stream().map(d -> d.amount()).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertTrue(debts.stream().allMatch(d -> d.fromMemberId().equals(3L)));
    }
}
