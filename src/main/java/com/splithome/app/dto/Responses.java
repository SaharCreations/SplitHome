package com.splithome.app.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class Responses {
    private Responses() {}

    public record HouseholdDto(Long id, String name) {}
    public record MemberDto(Long id, String name) {}
    public record ShareDto(Long memberId, String memberName, BigDecimal amount, BigDecimal settledAmount, BigDecimal remainingAmount, boolean settled) {}
    public record ExpenseDto(Long id, String description, BigDecimal amount, Long paidById, String paidByName, LocalDateTime createdAt, List<ShareDto> shares) {}
    public record ExpensePageDto(List<ExpenseDto> items, int page, int size, long totalItems, int totalPages, boolean hasNext) {}
    public record SettlementDto(Long id, Long fromMemberId, String fromMemberName, Long toMemberId, String toMemberName, BigDecimal amount, LocalDateTime createdAt, Long expenseId, String expenseDescription) {}
    public record BalanceDto(Long memberId, String memberName, BigDecimal balance) {}
    public record DebtDto(Long fromMemberId, String fromMemberName, Long toMemberId, String toMemberName, BigDecimal amount) {}
    public record DashboardDto(HouseholdDto household, List<MemberDto> members, List<SettlementDto> settlements, List<BalanceDto> balances, List<DebtDto> debts, BigDecimal totalSpent) {}
}
