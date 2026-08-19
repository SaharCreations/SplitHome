package com.splithome.app.service;

import com.splithome.app.dto.Responses.DebtDto;
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
    @Test void directDebtKeepsTheOriginalCreditor() {
        var debts = service().netDirectDebts(List.of(
                new DebtDto(3L, "Emily", 2L, "Ann", new BigDecimal("9.38")),
                new DebtDto(3L, "Emily", 1L, "Sahar", new BigDecimal("40.69"))), List.of());
        assertTrue(debts.stream().anyMatch(d -> d.fromMemberName().equals("Emily") && d.toMemberName().equals("Ann") && d.amount().equals(new BigDecimal("9.38"))));
        assertTrue(debts.stream().anyMatch(d -> d.fromMemberName().equals("Emily") && d.toMemberName().equals("Sahar") && d.amount().equals(new BigDecimal("40.69"))));
    }
    @Test void paymentReducesOnlyThatPair() {
        var debts = service().netDirectDebts(
                List.of(new DebtDto(3L, "Emily", 2L, "Ann", new BigDecimal("20.00"))),
                List.of(new DebtDto(3L, "Emily", 2L, "Ann", new BigDecimal("7.50"))));
        assertEquals(1, debts.size());
        assertEquals(new BigDecimal("12.50"), debts.get(0).amount());
    }
    @Test void oppositeExpensesNetOnlyBetweenTheSameTwoPeople() {
        var debts = service().netDirectDebts(List.of(
                new DebtDto(1L, "A", 2L, "B", new BigDecimal("30.00")),
                new DebtDto(2L, "B", 1L, "A", new BigDecimal("12.00"))), List.of());
        assertEquals(1, debts.size());
        assertEquals(1L, debts.get(0).fromMemberId());
        assertEquals(2L, debts.get(0).toMemberId());
        assertEquals(new BigDecimal("18.00"), debts.get(0).amount());
    }
}
